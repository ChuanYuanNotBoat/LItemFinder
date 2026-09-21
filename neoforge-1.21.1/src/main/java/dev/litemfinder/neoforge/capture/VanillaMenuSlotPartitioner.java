package dev.litemfinder.neoforge.capture;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
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
        return MenuPartition.unsupported("unsupported_menu:" + menu.getClass().getSimpleName());
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
