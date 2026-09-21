package dev.litemfinder.neoforge.lifecycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded FIFO-by-key buffer that coalesces pending work to the newest value for each key. */
public final class LatestValueBuffer<K, V> {

    private final int capacity;
    private final LinkedHashMap<K, V> values = new LinkedHashMap<>();

    public LatestValueBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
    }

    public synchronized OfferResult offer(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (values.containsKey(key)) {
            values.put(key, value);
            return OfferResult.REPLACED;
        }
        if (values.size() >= capacity) {
            return OfferResult.REJECTED_CAPACITY;
        }
        values.put(key, value);
        return OfferResult.ADDED;
    }

    public synchronized Optional<PendingValue<K, V>> poll() {
        var iterator = values.entrySet().iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        }
        Map.Entry<K, V> entry = iterator.next();
        PendingValue<K, V> pending = new PendingValue<>(entry.getKey(), entry.getValue());
        iterator.remove();
        return Optional.of(pending);
    }

    public synchronized List<PendingValue<K, V>> drain() {
        List<PendingValue<K, V>> pending = new ArrayList<>(values.size());
        values.forEach((key, value) -> pending.add(new PendingValue<>(key, value)));
        values.clear();
        return List.copyOf(pending);
    }

    public synchronized int size() {
        return values.size();
    }

    public enum OfferResult {
        ADDED,
        REPLACED,
        REJECTED_CAPACITY
    }

    public record PendingValue<K, V>(K key, V value) {

        public PendingValue {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }
    }
}
