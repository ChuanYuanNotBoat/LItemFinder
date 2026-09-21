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
import java.util.function.Supplier;

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
        if (!isAccepting()) {
            return;
        }
        awaitControl(() -> null, DEFAULT_CLOSE_TIMEOUT, "flush storage", null);
    }

    /** Drains current work and closes the active scope before the client leaves a world/server. */
    public void leaveScope() {
        if (!isAccepting()) {
            return;
        }
        awaitControl(() -> {
            closeCurrentScope();
            return null;
        }, DEFAULT_CLOSE_TIMEOUT, "leave scope", null);
    }

    /** Deletes all persisted and in-memory snapshots for the active scope. */
    public int clearCurrentScopeData() {
        if (!isAccepting()) {
            return -1;
        }
        return awaitControl(this::clearCurrentScopeDataOnExecutor,
                DEFAULT_CLOSE_TIMEOUT, "clear current scope data", -1);
    }

    /** Removes the observed snapshot only if it is still current after earlier writes finish. */
    public CompletableFuture<Boolean> removeCurrentScopeContainer(InventorySnapshot expectedSnapshot) {
        Objects.requireNonNull(expectedSnapshot, "expectedSnapshot must not be null");
        if (!isAccepting()) {
            return CompletableFuture.completedFuture(false);
        }
        ContainerId containerId = expectedSnapshot.container().id();
        String expectedScope = expectedSnapshot.container().location().scope();
        CompletableFuture<Boolean> removed = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    boolean snapshotIsCurrent = expectedScope.equals(currentScope)
                            && currentIndex.rootSnapshots().stream().anyMatch(expectedSnapshot::equals);
                    if (!snapshotIsCurrent) {
                        removed.complete(false);
                        return;
                    }
                    boolean repositoryRemoved = currentRepository != null && currentRepository.delete(containerId);
                    boolean indexRemoved = currentIndex.remove(containerId);
                    removed.complete(repositoryRemoved || indexRemoved);
                } catch (Throwable throwable) {
                    removed.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException exception) {
            removed.completeExceptionally(exception);
        }
        return removed;
    }

    @Override
    public void close() {
        synchronized (pendingLock) {
            if (!accepting) {
                return;
            }
            accepting = false;
        }
        awaitControl(() -> {
            closeCurrentScope();
            return null;
        }, DEFAULT_CLOSE_TIMEOUT, "shut down storage", null);
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

    private int clearCurrentScopeDataOnExecutor() {
        if (currentRepository == null) {
            currentIndex = new InMemoryStorageIndex();
            return 0;
        }
        var snapshots = currentRepository.findAll();
        for (InventorySnapshot snapshot : snapshots) {
            currentRepository.delete(snapshot.container().id());
        }
        currentIndex = new InMemoryStorageIndex();
        return snapshots.size();
    }

    private boolean isAccepting() {
        synchronized (pendingLock) {
            return accepting;
        }
    }

    private <T> T awaitControl(Supplier<T> action, Duration timeout, String description, T fallback) {
        CompletableFuture<T> completed = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    completed.complete(action.get());
                } catch (Throwable throwable) {
                    completed.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to schedule {}", description, exception);
            return fallback;
        }
        try {
            return completed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while attempting to {}", description, exception);
        } catch (TimeoutException exception) {
            LOGGER.warn("Timed out after {} ms while attempting to {}", timeout.toMillis(), description);
        } catch (ExecutionException exception) {
            LOGGER.error("Failed to {}", description, exception.getCause());
        }
        return fallback;
    }

    private record PendingSnapshot(String scope, InventorySnapshot snapshot, boolean persistable) {

        private PendingSnapshot {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(snapshot, "snapshot must not be null");
        }
    }
}
