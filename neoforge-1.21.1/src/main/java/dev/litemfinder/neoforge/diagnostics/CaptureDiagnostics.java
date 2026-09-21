package dev.litemfinder.neoforge.diagnostics;

import dev.litemfinder.neoforge.capture.CaptureReason;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/** Thread-safe counters exposed by the development command surface. */
public final class CaptureDiagnostics {

    private final ConcurrentMap<CaptureReason, LongAdder> captures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> skipped = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> degraded = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> removed = new ConcurrentHashMap<>();

    public void captured(CaptureReason reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        captures.computeIfAbsent(reason, ignored -> new LongAdder()).increment();
    }

    public void skipped(String reason) {
        increment(skipped, reason);
    }

    public void degraded(String reason) {
        increment(degraded, reason);
    }

    public void removed(String reason) {
        increment(removed, reason);
    }

    public Snapshot snapshot() {
        EnumMap<CaptureReason, Long> captureCounts = new EnumMap<>(CaptureReason.class);
        captures.forEach((reason, count) -> captureCounts.put(reason, count.sum()));
        return new Snapshot(captureCounts, copy(skipped), copy(degraded), copy(removed));
    }

    public void clear() {
        captures.clear();
        skipped.clear();
        degraded.clear();
        removed.clear();
    }

    private static void increment(ConcurrentMap<String, LongAdder> counters, String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        counters.computeIfAbsent(reason, ignored -> new LongAdder()).increment();
    }

    private static Map<String, Long> copy(ConcurrentMap<String, LongAdder> source) {
        Map<String, Long> result = new java.util.TreeMap<>();
        source.forEach((reason, count) -> result.put(reason, count.sum()));
        return Map.copyOf(result);
    }

    public record Snapshot(
            Map<CaptureReason, Long> captures,
            Map<String, Long> skipped,
            Map<String, Long> degraded,
            Map<String, Long> removed
    ) {

        public Snapshot {
            captures = Map.copyOf(Objects.requireNonNull(captures, "captures must not be null"));
            skipped = Map.copyOf(Objects.requireNonNull(skipped, "skipped must not be null"));
            degraded = Map.copyOf(Objects.requireNonNull(degraded, "degraded must not be null"));
            removed = Map.copyOf(Objects.requireNonNull(removed, "removed must not be null"));
        }

        public long totalCaptures() {
            return captures.values().stream().mapToLong(Long::longValue).sum();
        }

        public long totalSkipped() {
            return skipped.values().stream().mapToLong(Long::longValue).sum();
        }

        public long totalDegraded() {
            return degraded.values().stream().mapToLong(Long::longValue).sum();
        }

        public long totalRemoved() {
            return removed.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
