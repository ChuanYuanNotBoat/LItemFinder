package dev.litemfinder.neoforge.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluidInventoryAdaptersTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }
    @Test void installedBarrelDelegateContractReadsTankWithoutFillOrDrainAndRejectsOtherTypes() {
        Barrel owner = new Barrel();
        var fluid = FluidInventoryAdapters.readSyncedBarrel(owner, "tfc:barrel").orElseThrow();
        assertEquals(2000, fluid.tanks().getFirst().amount());
        assertEquals(10000, fluid.tanks().getFirst().capacity());
        assertEquals(1, owner.reads);
        assertTrue(FluidInventoryAdapters.readSyncedBarrel(owner, "tfc:groundcover").isEmpty());
        assertTrue(FluidInventoryAdapters.readSyncedBarrel(owner, "unknown:tank").isEmpty());
        assertEquals(1, owner.reads);
    }
    public static class Barrel extends BlockEntity {
        int reads;
        Barrel() { super(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState()); }
        public Composite getInventory() { reads++; return new Composite(); }
    }
    public static class Composite extends ItemStackHandler implements IFluidHandler {
        Composite() { super(3); }
        public int getTanks() { return 1; }
        public FluidStack getFluidInTank(int tank) { return new FluidStack(Fluids.WATER, 2000); }
        public int getTankCapacity(int tank) { return 10000; }
        public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        public int fill(FluidStack fluid, FluidAction action) { throw new AssertionError("read-only capture"); }
        public FluidStack drain(FluidStack fluid, FluidAction action) { throw new AssertionError("read-only capture"); }
        public FluidStack drain(int amount, FluidAction action) { throw new AssertionError("read-only capture"); }
    }
}
