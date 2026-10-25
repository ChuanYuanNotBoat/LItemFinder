package dev.litemfinder.neoforge.api.inventory;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.neoforge.identity.IdentityConfidence;
import dev.litemfinder.neoforge.identity.ResolvedContainerIdentity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** A detached visible page. Network identity must come from the provider; page/filter/sort belong in viewKey. */
public record StorageTerminalView(String networkId, String name, String viewKey, List<Window> windows) {
    public StorageTerminalView {
        if (networkId.isBlank() || networkId.length() > 512 || name.isBlank() || name.length() > 128
                || viewKey.length() > 512) throw new IllegalArgumentException("invalid terminal identity");
        windows = List.copyOf(windows);
        if (windows.size() > 64 || windows.stream().mapToInt(window -> window.slots().size()).sum() > 4096
                || windows.stream().map(window -> window.identity().container().id()).distinct().count() != windows.size()) {
            throw new IllegalArgumentException("invalid visible terminal windows");
        }
    }

    public record Window(ResolvedContainerIdentity identity, int slotCount, Map<Integer, ItemStack> slots,
                         boolean complete, Optional<FluidInventory> fluids) {
        public Window {
            // An unresolved aggregate has no proven physical membership and cannot safely be added to totals.
            if (identity.confidence() != IdentityConfidence.EXACT || !(identity.container().location() instanceof WorldLocation)
                    || slotCount < 0 || slotCount > 4096 || slots.keySet().stream().anyMatch(slot -> slot < 0 || slot >= slotCount)) {
                throw new IllegalArgumentException("terminal windows require exact physical storage and real slot indices");
            }
            Map<Integer, ItemStack> copied = new TreeMap<>();
            slots.forEach((slot, stack) -> copied.put(slot, stack.copy()));
            slots = java.util.Collections.unmodifiableMap(copied);
            java.util.Objects.requireNonNull(fluids);
            if (complete && slots.size() != slotCount) throw new IllegalArgumentException("complete window must include empty slots");
        }
    }
}
