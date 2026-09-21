package dev.litemfinder.neoforge.persistence;

import com.mojang.logging.LogUtils;
import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.index.StorageIndex;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.persistence.SnapshotIndexLoader;
import dev.litemfinder.core.persistence.SnapshotRepository;
import dev.litemfinder.neoforge.lifecycle.LatestValueBuffer;
import dev.litemfinder.storage.sqlite.SqliteSnapshotRepository;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Serializes immutable snapshots to one scope database without blocking the client tick. */
public final class SnapshotStorageCoordinator implements AutoCloseable {

    public static final int DEFAULT_PENDING_CAPACITY = 128;
    public static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(3);

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Object pendingLock = new Object();
    private final Path indexDirectory;
    private final LatestValueBuffer<ContainerId, PendingSnapshot> pending;
    private final ExecutorService executor;
    private final SnapshotIndexLoader indexLoader = new SnapshotIndexLoader();

    private volatile StorageIndex currentIndex = new InMemoryStorageIndex();
    private boolean drainScheduled;
    private boolean accepting = true;

    // Accessed only by the storage executor.
    private String currentScope;
    private SnapshotRepository currentRepository;

    public SnapshotStorageCoordinator(Path indexDirectory) {
        this(indexDirectory, DEFAULT_PENDING_CAPACITY);
    }

    public SnapshotStorageCoordinator(Path indexDirectory, int pendingCapacity) {
        this.indexDirectory = Objects.requireNonNull(indexDirectory, "indexDirectory must not be null")
                .toAbsolutePath()
                .normalize();
        pending = new LatestValueBuffer<>(pendingCapacity);
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "litemfinder-snapshot-storage");
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newSingleThreadExecutor(threads);
    }

    public LatestValueBuffer.OfferResult submit(InventorySnapshot snapshot, boolean persistable) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        String scope = snapshot.container().location().scope();
        synchronized (pendingLock) {
            if (!accepting) {
                return LatestValueBuffer.OfferResult.REJECTED_CAPACITY;
            }
            LatestValueBuffer.OfferResult result = pending.offer(
                    snapshot.container().id(),
                    new PendingSnapshot(scope, snapshot, persistable)
            );
            if (result != LatestValueBuffer.OfferResult.REJECTED_CAPACITY && !drainScheduled) {
                drainScheduled = true;
                executor.execute(this::drainPending);
            }
            return result;
        }
    }

    public StorageIndex currentIndex() {
        return currentIndex;
    }

    /** Waits for all work already submitted by the caller to finish without closing the active scope. */
    public void flush() {
        awaitControl(() -> {
        }, DEFAULT_CLOSE_TIMEOUT, "flush storage");
    }

    /** Drains current work and closes the active scope before the client leaves a world/server. */
    public void leaveScope() {
        awaitControl(this::closeCurrentScope, DEFAULT_CLOSE_TIMEOUT, "leave scope");
    }

    @Override
    public void close() {
        synchronized (pendingLock) {
            if (!accepting) {
                return;
            }
            accepting = false;
        }
        awaitControl(this::closeCurrentScope, DEFAULT_CLOSE_TIMEOUT, "shut down storage");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DEFAULT_CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    Path databaseFile(String scope) {
        String prefix = "scope:v1:";
        if (!scope.startsWith(prefix)) {
            throw new IllegalArgumentException("unsupported scope format");
        }
        String hash = scope.substring(prefix.length());
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("scope hash must be lowercase SHA-256");
        }
        return indexDirectory.resolve(hash + ".db");
    }

    private void drainPending() {
        while (true) {
            Optional<LatestValueBuffer.PendingValue<ContainerId, PendingSnapshot>> next;
            synchronized (pendingLock) {
                next = pending.poll();
                if (next.isEmpty()) {
                    drainScheduled = false;
                    return;
                }
            }
            persist(next.orElseThrow().value());
        }
    }

    private void persist(PendingSnapshot pendingSnapshot) {
        try {
            ensureScope(pendingSnapshot.scope());
            if (pendingSnapshot.persistable()) {
                currentRepository.save(pendingSnapshot.snapshot());
            }
            currentIndex.update(pendingSnapshot.snapshot());
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to store snapshot {} in scope {}",
                    pendingSnapshot.snapshot().container().id(),
                    pendingSnapshot.scope(),
                    exception
            );
        }
    }

    private void ensureScope(String scope) {
        if (scope.equals(currentScope)) {
            return;
        }
        closeCurrentScope();
        SnapshotRepository repository = new SqliteSnapshotRepository(databaseFile(scope));
        InMemoryStorageIndex restoredIndex = new InMemoryStorageIndex();
        int restored = indexLoader.load(repository, restoredIndex);
        currentRepository = repository;
        currentScope = scope;
        currentIndex = restoredIndex;
        LOGGER.info("LItem Finder opened scope database and restored {} root snapshots", restored);
    }

    private void closeCurrentScope() {
        if (currentRepository != null) {
            currentRepository.close();
        }
        currentRepository = null;
        currentScope = null;
        currentIndex = new InMemoryStorageIndex();
    }

    private void awaitControl(Runnable action, Duration timeout, String description) {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                action.run();
                completed.complete(null);
            } catch (Throwable throwable) {
                completed.completeExceptionally(throwable);
            }
        });
        try {
            completed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while attempting to {}", description, exception);
        } catch (TimeoutException exception) {
            LOGGER.warn("Timed out after {} ms while attempting to {}", timeout.toMillis(), description);
        } catch (ExecutionException exception) {
            LOGGER.error("Failed to {}", description, exception.getCause());
        }
    }

    private record PendingSnapshot(String scope, InventorySnapshot snapshot, boolean persistable) {

        private PendingSnapshot {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(snapshot, "snapshot must not be null");
        }
    }
}
