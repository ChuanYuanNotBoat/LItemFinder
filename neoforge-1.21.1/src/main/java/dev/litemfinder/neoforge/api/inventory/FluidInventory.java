package dev.litemfinder.neoforge.api.inventory;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Detached fluid observations in millibuckets. Amount is long even when the platform handler uses int. */
public record FluidInventory(List<Tank> tanks) {
    public static final int MAX_TANKS = dev.litemfinder.core.snapshot.FluidSnapshotMetadata.MAX_TANKS;
    public FluidInventory {
        tanks = List.copyOf(tanks);
        if (tanks.size() > MAX_TANKS) throw new IllegalArgumentException("too many tanks");
        var indices = new HashSet<Integer>();
        if (tanks.stream().anyMatch(tank -> !indices.add(tank.index()))) throw new IllegalArgumentException("duplicate tank");
    }

    public static FluidInventory fromHandler(IFluidHandler handler) {
        int size = handler.getTanks();
        if (size < 0 || size > MAX_TANKS) throw new IllegalArgumentException("invalid tank count");
        return new FluidInventory(IntStream.range(0, size).mapToObj(index -> {
            FluidStack fluid = handler.getFluidInTank(index);
            return new Tank(index, fluid, fluid.getAmount(), handler.getTankCapacity(index));
        }).toList());
    }

    public long fingerprint() {
        long hash = 1;
        for (Tank tank : tanks) {
            hash = 31 * hash + tank.index();
            hash = 31 * hash + tank.fluid().getFluid().hashCode();
            hash = 31 * hash + tank.fluid().getComponents().hashCode();
            hash = 31 * hash + tank.amount();
            hash = 31 * hash + tank.capacity();
        }
        return hash;
    }

    public record Tank(int index, FluidStack fluid, long amount, long capacity) {
        public Tank {
            Objects.requireNonNull(fluid);
            if (index < 0 || index >= MAX_TANKS || amount < 0 || capacity < 0
                    || (amount > 0 && fluid.isEmpty())) throw new IllegalArgumentException("invalid fluid tank");
            fluid = amount == 0 ? FluidStack.EMPTY : fluid.copyWithAmount(1);
        }
    }
}
