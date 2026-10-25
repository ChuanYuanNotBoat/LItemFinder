package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.MenuStorage;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Conservative slot policy for the first supported vanilla storage menus. */
public final class VanillaMenuSlotPartitioner {

    private final boolean includePlayerInventory;

    public VanillaMenuSlotPartitioner(boolean includePlayerInventory) {
        this.includePlayerInventory = includePlayerInventory;
    }

    public MenuPartition partition(AbstractContainerMenu menu, Inventory playerInventory) {
        Objects.requireNonNull(menu, "menu must not be null");
        Objects.requireNonNull(playerInventory, "playerInventory must not be null");
        if (TerminalMenuAdapters.find(menu).isPresent()) return MenuPartition.unsupported("terminal_managed");

        var modStorage = StorageMenuAdapters.resolve(menu);
        if (modStorage.isPresent()) {
            return selectBackingStorage(menu, playerInventory, modStorage.orElseThrow());
        }

        if (menu instanceof InventoryMenu) {
            return includePlayerInventory
                    ? select(menu, playerInventory, true, "player_inventory")
                    : MenuPartition.unsupported("player_inventory_disabled");
        }
        if (menu instanceof ChestMenu) {
            return select(menu, playerInventory, false, "chest_like");
        }
        if (menu instanceof ShulkerBoxMenu) {
            return select(menu, playerInventory, false, "shulker_box");
        }
        if (menu instanceof AbstractFurnaceMenu) {
            return selectExpectedStorage(menu, playerInventory, 3, "furnace_like");
        }
        if (menu instanceof HopperMenu) {
            return selectExpectedStorage(menu, playerInventory, 5, "hopper");
        }
        if (menu instanceof BrewingStandMenu) {
            return selectExpectedStorage(menu, playerInventory, 5, "brewing_stand");
        }
        if (menu instanceof DispenserMenu) {
            return selectExpectedStorage(menu, playerInventory, 9, "dispenser_like");
        }
        if (menu instanceof CrafterMenu) {
            return selectFirstSlots(menu, playerInventory, 9, "crafter");
        }
        return MenuPartition.unsupported("unsupported_menu:" + menu.getClass().getSimpleName());
    }

    private static MenuPartition selectBackingStorage(AbstractContainerMenu menu, Inventory player,
                                                       MenuStorage storage) {
        List<MenuSlotRef> selected = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.container == player) continue;
            java.util.OptionalInt matched;
            try {
                matched = storage.slotIndex(slot);
            } catch (IllegalArgumentException invalidSlot) {
                return MenuPartition.unsupported("unexpected_layout:mod_storage");
            }
            if (matched.isEmpty()) continue;
            int backingSlot = matched.getAsInt();
            // Some menus render multiple views of one real slot. Count its contents once.
            if (seen.add(backingSlot)) selected.add(new MenuSlotRef(index, backingSlot));
        }
        if (selected.isEmpty()) return MenuPartition.unsupported("no_trusted_slots:mod_storage");
        selected.sort(java.util.Comparator.comparingInt(MenuSlotRef::containerSlot));
        return MenuPartition.supported("mod_storage:" + menu.getClass().getSimpleName(), selected);
    }

    private static MenuPartition selectExpectedStorage(
            AbstractContainerMenu menu,
            Inventory playerInventory,
            int expectedSlots,
            String kind
    ) {
        MenuPartition selected = select(menu, playerInventory, false, kind);
        if (!selected.supported() || selected.slots().size() != expectedSlots) {
            return MenuPartition.unsupported("unexpected_layout:" + kind);
        }
        return selected;
    }

    private static MenuPartition selectFirstSlots(
            AbstractContainerMenu menu,
            Inventory playerInventory,
            int slotCount,
            String kind
    ) {
        if (menu.slots.size() < slotCount) {
            return MenuPartition.unsupported("unexpected_layout:" + kind);
        }
        List<MenuSlotRef> selected = new ArrayList<>(slotCount);
        for (int menuSlot = 0; menuSlot < slotCount; menuSlot++) {
            Slot slot = menu.slots.get(menuSlot);
            if (slot.container == playerInventory) {
                return MenuPartition.unsupported("unexpected_layout:" + kind);
            }
            selected.add(new MenuSlotRef(menuSlot, slot.getContainerSlot()));
        }
        selected.sort((left, right) -> Integer.compare(left.containerSlot(), right.containerSlot()));
        return MenuPartition.supported(kind, selected);
    }

    private static MenuPartition select(
            AbstractContainerMenu menu,
            Inventory playerInventory,
            boolean selectPlayer,
            String kind
    ) {
        List<MenuSlotRef> selected = new ArrayList<>();
        List<Slot> slots = menu.slots;
        for (int menuSlot = 0; menuSlot < slots.size(); menuSlot++) {
            Slot slot = slots.get(menuSlot);
            if ((slot.container == playerInventory) == selectPlayer) {
                selected.add(new MenuSlotRef(menuSlot, slot.getContainerSlot()));
            }
        }
        if (selected.isEmpty()) {
            return MenuPartition.unsupported("no_trusted_slots:" + kind);
        }
        selected.sort((left, right) -> Integer.compare(left.containerSlot(), right.containerSlot()));
        return MenuPartition.supported(kind, selected);
    }
}
