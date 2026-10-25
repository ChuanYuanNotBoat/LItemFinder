package dev.litemfinder.neoforge.compat;

import dev.litemfinder.neoforge.api.inventory.FluidInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/** Reads detached upgrade stacks from an existing cache. Never constructs a live backpack/upgrade wrapper. */
public final class SophisticatedBackpackTankContents {
    private static final Optional<Access> API = access();
    private SophisticatedBackpackTankContents() { }

    public static Optional<FluidInventory> readLoose(ItemStack stack) {
        if (API.isEmpty() || !API.orElseThrow().tankType().isInstance(stack.getItem())) return Optional.empty();
        try {
            var fluid = ((SimpleFluidContent) API.orElseThrow().contents().invoke(null, stack)).copy();
            return Optional.of(new FluidInventory(List.of(new FluidInventory.Tank(0, fluid, fluid.getAmount(), 0))));
        } catch (ReflectiveOperationException | RuntimeException failure) { return Optional.empty(); }
    }
    public static boolean isTankUpgrade(ItemStack stack) {
        return API.isPresent() && API.orElseThrow().tankType().isInstance(stack.getItem());
    }
    public static Optional<FluidInventory> readSynced(ItemStack backpack, CompoundTag contents, HolderLookup.Provider registries) {
        if (API.isEmpty()) return Optional.empty();
        try {
            var api = API.orElseThrow();
            CompoundTag upgrades = contents.getCompound("upgradeInventory");
            int inventorySlots = contents.getCompound("inventory").getInt("Size");
            int rows = (int) Math.ceil(inventorySlots / (inventorySlots > 81 ? 12.0 : 9.0));
            List<Map.Entry<Integer, ItemStack>> parsed = new ArrayList<>();
            for (Tag entry : upgrades.getList("Items", Tag.TAG_COMPOUND)) {
                CompoundTag item = ((CompoundTag) entry).copy();
                int slot = item.getInt("Slot");
                if (slot < 0 || slot >= FluidInventory.MAX_TANKS) return Optional.empty();
                item.putInt("count", 1);
                var decoded = ItemStack.parse(registries, item);
                if (decoded.isEmpty()) return Optional.empty();
                parsed.add(Map.entry(slot, decoded.orElseThrow()));
            }
            double multiplier = 1;
            for (var entry : parsed) if (api.stackType().isInstance(entry.getValue().getItem())) {
                multiplier *= ((Number) api.multiplier().invoke(entry.getValue().getItem())).doubleValue();
            }
            // Installed 1.4.81 uses the clamped inventory slot limit / 64f as its stack multiplier.
            int slotLimit = (int) Math.min(Integer.MAX_VALUE, Math.max(1, 64 * multiplier));
            double actualMultiplier = slotLimit / 64f;
            Object storage = Proxy.newProxyInstance(api.storageType().getClassLoader(), new Class<?>[]{api.storageType()},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getNumberOfSlotRows")) return rows;
                        if (method.getName().equals("toString")) return "read-only tank capacity context";
                        throw new UnsupportedOperationException("No live storage access: " + method.getName());
                    });
            List<FluidInventory.Tank> tanks = new ArrayList<>();
            for (var entry : parsed) {
                ItemStack upgrade = entry.getValue();
                if (!api.tankType().isInstance(upgrade.getItem())) continue;
                var fluid = ((SimpleFluidContent) api.contents().invoke(null, upgrade)).copy();
                int base = ((Number) api.baseCapacity().invoke(upgrade.getItem(), storage)).intValue();
                Object config = api.config().invoke(upgrade.getItem());
                Object ratioValue = config.getClass().getField("stackMultiplierRatio").get(config);
                double ratio = ((Number) ratioValue.getClass().getMethod("get").invoke(ratioValue)).doubleValue();
                long capacity = (long) Math.min(Integer.MAX_VALUE, base * (1 + ratio * (actualMultiplier - 1)));
                tanks.add(new FluidInventory.Tank(entry.getKey(), fluid, fluid.getAmount(), capacity));
            }
            return Optional.of(new FluidInventory(tanks));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return Optional.empty(); }
    }
    private static Optional<Access> access() {
        try {
            var loader = SophisticatedBackpackTankContents.class.getClassLoader();
            var tank = Class.forName("net.p3pp3rf1y.sophisticatedcore.upgrades.tank.TankUpgradeItem", false, loader);
            var wrapper = Class.forName("net.p3pp3rf1y.sophisticatedcore.upgrades.tank.TankUpgradeWrapper", false, loader);
            var stack = Class.forName("net.p3pp3rf1y.sophisticatedcore.upgrades.stack.StackUpgradeItem", false, loader);
            var storage = Class.forName("net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper", false, loader);
            return Optional.of(new Access(tank, stack, storage, wrapper.getMethod("getContents", ItemStack.class),
                    stack.getMethod("getStackSizeMultiplier"), tank.getMethod("getBaseCapacity", storage),
                    tank.getMethod("getTankUpgradeConfig")));
        } catch (ReflectiveOperationException | LinkageError absent) { return Optional.empty(); }
    }
    private record Access(Class<?> tankType, Class<?> stackType, Class<?> storageType, Method contents,
                          Method multiplier, Method baseCapacity, Method config) { }
}
