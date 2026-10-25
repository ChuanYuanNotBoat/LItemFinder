package dev.litemfinder.neoforge.compat;

import com.mojang.serialization.Codec;
import dev.litemfinder.neoforge.api.inventory.FluidInventory;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Optional;

/** Passive Jade bridge: only its already received, verified data for the exact current target is read. */
public final class JadeFluidSnapshotReader {
    private JadeFluidSnapshotReader() { }
    public static Optional<FluidInventory> read(BlockEntity owner) {
        try {
            var loader = JadeFluidSnapshotReader.class.getClassLoader();
            Class<?> center = Class.forName("snownee.jade.impl.ObjectDataCenter", false, loader);
            Class<?> blockAccessor = Class.forName("snownee.jade.api.BlockAccessor", false, loader);
            Class<?> accessorType = Class.forName("snownee.jade.api.Accessor", false, loader);
            Object accessor = center.getMethod("get").invoke(null);
            if (!blockAccessor.isInstance(accessor) || owner.getLevel() == null
                    || blockAccessor.getMethod("isFakeBlock").invoke(accessor).equals(true)
                    || accessorType.getMethod("getLevel").invoke(accessor) != owner.getLevel()
                    || !owner.getBlockPos().equals(blockAccessor.getMethod("getPosition").invoke(accessor))) return Optional.empty();
            CompoundTag data = (CompoundTag) accessorType.getMethod("getServerData").invoke(accessor);
            if (!Boolean.TRUE.equals(accessorType.getMethod("isServerConnected").invoke(accessor))
                    || !Boolean.TRUE.equals(accessorType.getMethod("verifyData", CompoundTag.class).invoke(accessor, data))
                    || !data.contains("JadeFluidStorage", Tag.TAG_LIST) || !data.contains("JadeFluidStorageUid", Tag.TAG_STRING)) return Optional.empty();
            Class<?> jadeFluid = Class.forName("snownee.jade.api.fluid.JadeFluidObject", false, loader);
            Codec<?> codec = (Codec<?>) jadeFluid.getField("CODEC").get(null);
            return decode(data, raw -> {
                try {
                    Object value = codec.parse(owner.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), raw).getOrThrow();
                    long amount = ((Number) jadeFluid.getMethod("getAmount").invoke(value)).longValue();
                    Fluid type = (Fluid) jadeFluid.getMethod("getType").invoke(value);
                    var fluid = amount == 0 ? FluidStack.EMPTY : new FluidStack(type, 1);
                    if (amount > 0) fluid.applyComponents((DataComponentPatch) jadeFluid.getMethod("getComponents").invoke(value));
                    return new DecodedFluid(fluid, amount);
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return Optional.empty(); }
    }

    static Optional<FluidInventory> decode(CompoundTag data, java.util.function.Function<Tag, DecodedFluid> decoder) {
        try {
            if (!data.contains("JadeFluidStorage", Tag.TAG_LIST) || !data.contains("JadeFluidStorageUid", Tag.TAG_STRING)) return Optional.empty();
            var tanks = new ArrayList<FluidInventory.Tank>();
            for (Tag group : data.getList("JadeFluidStorage", Tag.TAG_COMPOUND)) {
                if (!((CompoundTag) group).contains("Views", Tag.TAG_LIST)) return Optional.empty();
                for (Tag raw : ((CompoundTag) group).getList("Views", Tag.TAG_COMPOUND)) {
                    CompoundTag view = (CompoundTag) raw;
                    if (!view.contains("capacity", Tag.TAG_ANY_NUMERIC) || !view.contains("fluid")) return Optional.empty();
                    var decoded = decoder.apply(view.get("fluid"));
                    tanks.add(new FluidInventory.Tank(tanks.size(), decoded.fluid(), decoded.amount(), view.getLong("capacity")));
                }
            }
            // An omitted/filtered empty group list is not proof that a previously indexed tank drained.
            return tanks.isEmpty() ? Optional.empty() : Optional.of(new FluidInventory(tanks));
        } catch (RuntimeException | LinkageError unavailable) { return Optional.empty(); }
    }
    record DecodedFluid(FluidStack fluid, long amount) { }
}
