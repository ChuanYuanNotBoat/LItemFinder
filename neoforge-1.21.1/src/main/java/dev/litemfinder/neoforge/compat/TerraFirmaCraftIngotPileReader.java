package dev.litemfinder.neoforge.compat;

import dev.litemfinder.neoforge.api.inventory.BlockInventory;
import dev.litemfinder.neoforge.api.inventory.BlockInventoryAdapter;
import dev.litemfinder.neoforge.api.inventory.InventoryLayout;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the synced per-ingot entries, not a visual block height or a synthesized pick result. */
public final class TerraFirmaCraftIngotPileReader implements BlockInventoryAdapter {
    private final Optional<Access> access;
    public TerraFirmaCraftIngotPileReader() {
        Optional<Access> found;
        try {
            var loader = getClass().getClassLoader();
            found = Optional.of(accessFor(Class.forName("net.dries007.tfc.common.blockentities.IngotPileBlockEntity", false, loader),
                    Class.forName("net.dries007.tfc.common.blockentities.IngotPileBlockEntity$Entry", false, loader)));
        } catch (ReflectiveOperationException | LinkageError absent) { found = Optional.empty(); }
        access = found;
    }
    TerraFirmaCraftIngotPileReader(Class<?> owner, Class<?> entry) throws ReflectiveOperationException { access = Optional.of(accessFor(owner, entry)); }
    private static Access accessFor(Class<?> owner, Class<?> entry) throws ReflectiveOperationException {
        Field entries = owner.getDeclaredField("entries"), stack = entry.getDeclaredField("stack");
        if (!entries.trySetAccessible() || !stack.trySetAccessible()) throw new IllegalAccessException("ingot entries inaccessible");
        return new Access(owner, entries, stack);
    }
    public Optional<BlockInventory> read(BlockEntity owner) {
        if (access.isEmpty() || !access.orElseThrow().owner().isInstance(owner)) return Optional.empty();
        try {
            var api = access.orElseThrow();
            if (!(api.entries().get(owner) instanceof List<?> entries) || entries.size() > BlockInventory.MAX_SLOTS) return Optional.empty();
            var stacks = new ArrayList<ItemStack>();
            for (Object entry : entries) {
                if (!(api.stack().get(entry) instanceof ItemStack stack)) return Optional.empty();
                stacks.add(stack);
            }
            return Optional.of(new BlockInventory("tfc:ingot_pile", stacks).withLayout(InventoryLayout.grid(stacks.size(), 8)));
        } catch (ReflectiveOperationException | RuntimeException failure) { return Optional.empty(); }
    }
    private record Access(Class<?> owner, Field entries, Field stack) { }
}
