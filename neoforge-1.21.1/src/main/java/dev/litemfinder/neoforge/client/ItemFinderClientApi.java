package dev.litemfinder.neoforge.client;

import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.search.SearchQuery;
import dev.litemfinder.core.search.SearchResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UI-neutral access to the live client index.
 *
 * <p>The future search panel, HUD and diagnostics screen should depend on this boundary instead of
 * the debug-command implementation or the storage coordinator.</p>
 */
public interface ItemFinderClientApi {

    SearchResponse search(SearchQuery query);

    /** Looks up every indexed variant of one Minecraft item ID. */
    ItemLookup findByItemId(NamespacedId itemId);

    IndexStatus status();

    /**
     * Deletes the active world's/server's index.
     *
     * <p>This method deliberately does not own confirmation UX. A command or GUI must obtain an
     * explicit confirmation before invoking it.</p>
     */
    ClearResult clearCurrentScopeData();

    record ItemLookup(NamespacedId itemId, List<StorageEntry> entries) {

        public ItemLookup {
            Objects.requireNonNull(itemId, "itemId must not be null");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
            if (entries.stream().anyMatch(entry -> !entry.stack().item().itemId().equals(itemId))) {
                throw new IllegalArgumentException("all entries must match itemId");
            }
        }

        public long totalCount() {
            return entries.stream().mapToLong(entry -> entry.stack().count()).sum();
        }

        public long variantCount() {
            return entries.stream().map(entry -> entry.stack().item()).distinct().count();
        }

        public long rootContainerCount() {
            return entries.stream().map(entry -> entry.rootContainer().id()).distinct().count();
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    record IndexStatus(
            int rootContainers,
            int entries,
            long variants,
            int cachedTagItems,
            CaptureStatus capture
    ) {

        public IndexStatus {
            if (rootContainers < 0 || entries < 0 || variants < 0 || cachedTagItems < 0) {
                throw new IllegalArgumentException("index status counts must not be negative");
            }
            Objects.requireNonNull(capture, "capture must not be null");
        }
    }

    record CaptureStatus(
            long captured,
            long skipped,
            long sessionOnly,
            long removed,
            Map<String, Long> captureReasons,
            Map<String, Long> skippedReasons,
            Map<String, Long> sessionOnlyReasons,
            Map<String, Long> removalReasons
    ) {

        public CaptureStatus {
            if (captured < 0 || skipped < 0 || sessionOnly < 0 || removed < 0) {
                throw new IllegalArgumentException("capture status counts must not be negative");
            }
            captureReasons = Map.copyOf(Objects.requireNonNull(
                    captureReasons,
                    "captureReasons must not be null"
            ));
            skippedReasons = Map.copyOf(Objects.requireNonNull(
                    skippedReasons,
                    "skippedReasons must not be null"
            ));
            sessionOnlyReasons = Map.copyOf(Objects.requireNonNull(
                    sessionOnlyReasons,
                    "sessionOnlyReasons must not be null"
            ));
            removalReasons = Map.copyOf(Objects.requireNonNull(
                    removalReasons,
                    "removalReasons must not be null"
            ));
        }
    }

    record ClearResult(boolean successful, int deletedRootSnapshots) {

        public ClearResult {
            if (successful && deletedRootSnapshots < 0) {
                throw new IllegalArgumentException("a successful clear must have a non-negative count");
            }
            if (!successful && deletedRootSnapshots != 0) {
                throw new IllegalArgumentException("a failed clear must report zero deleted roots");
            }
        }

        public static ClearResult success(int deletedRootSnapshots) {
            return new ClearResult(true, deletedRootSnapshots);
        }

        public static ClearResult failure() {
            return new ClearResult(false, 0);
        }
    }
}
