package dev.litemfinder.neoforge.client.view;

import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerPath;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.search.SearchResponse;
import dev.litemfinder.core.search.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Greedy, read-only allocation of exact requests to recorded source slots. */
public record AcquisitionPlan(List<Pick> picks, Map<ItemKey, Long> missing) {

    public AcquisitionPlan {
        picks = List.copyOf(Objects.requireNonNull(picks, "picks must not be null"));
        missing = Map.copyOf(Objects.requireNonNull(missing, "missing must not be null"));
    }

    public static AcquisitionPlan allocate(SearchResponse response, Map<ItemKey, Long> requests) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(requests, "requests must not be null");
        Map<ItemKey, List<StorageEntry>> sources = new HashMap<>();
        for (SearchResult result : response.results()) {
            sources.put(result.item(), result.entries());
        }
        ReservationLedger ledger = new ReservationLedger();
        List<Pick> picks = new ArrayList<>();
        Map<ItemKey, Long> missing = new LinkedHashMap<>();
        requests.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(request -> {
            if (request.getValue() == null || request.getValue() < 0) {
                throw new IllegalArgumentException("request counts must be non-negative");
            }
            long remaining = request.getValue();
            List<StorageEntry> entries = sources.getOrDefault(request.getKey(), List.of()).stream()
                    .sorted(Comparator.comparing(StorageEntry::observedAt).reversed()
                            .thenComparing(entry -> entry.rootContainer().id())
                            .thenComparingInt(StorageEntry::slot))
                    .toList();
            for (StorageEntry entry : entries) {
                if (remaining == 0) {
                    break;
                }
                long allocated = ledger.reserve(entry, remaining);
                if (allocated > 0) {
                    picks.add(new Pick(request.getKey(), entry, allocated));
                    remaining -= allocated;
                }
            }
            if (remaining > 0) {
                missing.put(request.getKey(), remaining);
            }
        });
        return new AcquisitionPlan(picks, missing);
    }

    public record Pick(ItemKey item, StorageEntry source, long count) {

        public Pick {
            Objects.requireNonNull(item, "item must not be null");
            Objects.requireNonNull(source, "source must not be null");
            if (!item.equals(source.stack().item()) || count <= 0 || count > source.stack().count()) {
                throw new IllegalArgumentException("pick must match an available exact item stack");
            }
        }
    }

    /** Reusable source ledger for retrieval and future prerequisite tasks. */
    public static final class ReservationLedger {

        private final Map<SourceSlot, Long> used = new HashMap<>();

        public long reserve(StorageEntry source, long wanted) {
            Objects.requireNonNull(source, "source must not be null");
            if (wanted < 0) {
                throw new IllegalArgumentException("wanted must be non-negative");
            }
            SourceSlot slot = new SourceSlot(source.rootContainer().id(), source.path(), source.slot());
            long available = Math.max(0, source.stack().count() - used.getOrDefault(slot, 0L));
            long taken = Math.min(available, wanted);
            if (taken > 0) {
                used.merge(slot, taken, Math::addExact);
            }
            return taken;
        }

        public void clear() {
            used.clear();
        }
    }

    public record SourceSlot(ContainerId root, ContainerPath path, int slot) {

        public SourceSlot {
            Objects.requireNonNull(root, "root must not be null");
            Objects.requireNonNull(path, "path must not be null");
            if (!root.equals(path.root()) || slot < 0) {
                throw new IllegalArgumentException("source slot must belong to root and be non-negative");
            }
        }
    }
}
