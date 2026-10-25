package dev.litemfinder.neoforge.compat;

import com.mojang.logging.LogUtils;
import dev.litemfinder.neoforge.api.inventory.BlockInventory;
import dev.litemfinder.neoforge.api.inventory.BlockInventoryAdapter;
import dev.litemfinder.neoforge.api.inventory.InventoryLayout;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Function;

/** Concrete, synchronized TFC display storage; never converts groundcover or item entities to stock. */
public final class TerraFirmaCraftDisplayInventoryReader implements BlockInventoryAdapter {
    private final Optional<Access> access;
    private final Function<BlockEntity, ResourceLocation> typeIds;
    private boolean warned;

    public TerraFirmaCraftDisplayInventoryReader() {
        access = findAccess();
        typeIds = owner -> BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType());
    }

    TerraFirmaCraftDisplayInventoryReader(Class<?> inventoryType, Class<?> placedType,
            Function<BlockEntity, ResourceLocation> typeIds) throws ReflectiveOperationException {
        access = Optional.of(accessFor(inventoryType, placedType));
        this.typeIds = typeIds;
    }

    @Override
    public Optional<BlockInventory> read(BlockEntity owner) {
        ResourceLocation id = typeIds.apply(owner);
        // The positive type list is checked before invoking ANY getter or interpreting an item.
        if (id == null || !id.getNamespace().equals("tfc")
                || !(id.getPath().equals("tool_rack") || id.getPath().equals("placed_item"))
                || access.isEmpty()) return Optional.empty();
        Access api = access.orElseThrow();
        if (!api.inventoryType().isInstance(owner)) return Optional.empty();
        try {
            if (!(api.inventory().invoke(owner) instanceof IItemHandler handler) || handler.getSlots() != 4) {
                return Optional.empty();
            }
            boolean large = id.getPath().equals("placed_item") && api.placedType().isInstance(owner)
                    && Boolean.TRUE.equals(api.large().invoke(owner));
            // Large placed items occupy real slot 0; small placed items and tool racks use four slots.
            int slots = large ? 1 : 4;
            return Optional.of(BlockInventory.fromHandler(id.toString(), handler, slots)
                    .withLayout(InventoryLayout.grid(slots, large ? 1 : 2)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (!warned) LogUtils.getLogger().warn("TFC display inventory capture is unavailable", exception);
            warned = true;
            return Optional.empty();
        }
    }

    private static Optional<Access> findAccess() {
        try {
            ClassLoader loader = TerraFirmaCraftDisplayInventoryReader.class.getClassLoader();
            return Optional.of(accessFor(Class.forName("net.dries007.tfc.common.blockentities.InventoryBlockEntity", false, loader),
                    Class.forName("net.dries007.tfc.common.blockentities.PlacedItemBlockEntity", false, loader)));
        } catch (ClassNotFoundException absent) {
            return Optional.empty();
        } catch (ReflectiveOperationException | LinkageError exception) {
            LogUtils.getLogger().warn("TFC display inventory API is incompatible", exception);
            return Optional.empty();
        }
    }

    private static Access accessFor(Class<?> inventoryType, Class<?> placedType) throws ReflectiveOperationException {
        return new Access(inventoryType, placedType, inventoryType.getMethod("getInventory"),
                placedType.getMethod("holdingLargeItem"));
    }

    private record Access(Class<?> inventoryType, Class<?> placedType, Method inventory, Method large) { }
}
