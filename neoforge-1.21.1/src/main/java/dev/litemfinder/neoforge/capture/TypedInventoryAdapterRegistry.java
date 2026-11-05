package dev.litemfinder.neoforge.capture;

import com.mojang.logging.LogUtils;
import dev.litemfinder.neoforge.api.inventory.InventoryReadResult;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/** Loader extension dispatch; domain snapshots and merging remain in Core. Higher priority runs first. */
public final class TypedInventoryAdapterRegistry<C, R> {
    private final List<Entry<C, R>> entries = new ArrayList<>();
    private volatile ClassValue<List<Entry<C, R>>> candidates = cache(List.of());

    public synchronized <T extends C> AutoCloseable register(ResourceLocation id, Class<T> type, int priority,
            Function<? super T, InventoryReadResult<R>> reader) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(reader, "reader must not be null");
        if (entries.stream().anyMatch(entry -> entry.id.equals(id))) {
            throw new IllegalArgumentException("duplicate inventory adapter: " + id);
        }
        Entry<C, R> entry = new Entry<>(id, type, priority, context -> reader.apply(type.cast(context)));
        entries.add(entry);
        refresh();
        return () -> remove(entry);
    }

    public InventoryReadResult<R> read(C context) {
        Objects.requireNonNull(context, "context must not be null");
        for (Entry<C, R> entry : candidates.get(context.getClass())) {
            try {
                InventoryReadResult<R> result = Objects.requireNonNull(entry.reader.apply(context));
                if (result.state() != InventoryReadResult.State.UNSUPPORTED) return result;
            } catch (RuntimeException | LinkageError failure) {
                entry.failure = failure.getClass().getName();
                if (entry.failures.incrementAndGet() == 1) {
                    LogUtils.getLogger().warn("Inventory adapter {} failed; preserving its last observation ({})",
                            entry.id, entry.failure);
                }
                // It matched the registered type. Trying a generic default could falsely clear old data.
                return InventoryReadResult.unavailable();
            }
        }
        return InventoryReadResult.unsupported();
    }

    public synchronized List<Failure> failures() {
        return entries.stream().filter(entry -> entry.failures.get() > 0)
                .map(entry -> new Failure(entry.id, entry.failures.get(), entry.failure)).toList();
    }

    public record Failure(ResourceLocation id, long count, String exceptionType) { }

    private synchronized void remove(Entry<C, R> entry) {
        if (entries.remove(entry)) refresh();
    }

    private void refresh() {
        entries.sort(Comparator.<Entry<C, R>>comparingInt(entry -> entry.priority).reversed()
                .thenComparing(entry -> entry.id.toString()));
        candidates = cache(List.copyOf(entries));
    }

    private static <C, R> ClassValue<List<Entry<C, R>>> cache(List<Entry<C, R>> snapshot) {
        return new ClassValue<>() {
            @Override protected List<Entry<C, R>> computeValue(Class<?> type) {
                return snapshot.stream().filter(entry -> entry.type.isAssignableFrom(type)).toList();
            }
        };
    }

    private static final class Entry<C, R> {
        private final ResourceLocation id;
        private final Class<?> type;
        private final int priority;
        private final Function<C, InventoryReadResult<R>> reader;
        private final AtomicLong failures = new AtomicLong();
        private volatile String failure = "";
        private Entry(ResourceLocation id, Class<?> type, int priority,
                Function<C, InventoryReadResult<R>> reader) {
            this.id = id;
            this.type = type;
            this.priority = priority;
            this.reader = reader;
        }
    }
}
