package dev.litemfinder.neoforge.compat;

import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class JadeFluidSnapshotReaderTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }
    @Test void missingFilteredAndMalformedViewsAreUnavailableButExplicitEmptyTankCanClear() {
        assertTrue(JadeFluidSnapshotReader.decode(new CompoundTag(), raw -> { throw new AssertionError(); }).isEmpty());
        var data = new CompoundTag();
        data.putString("JadeFluidStorageUid", "jade:default_fluid");
        var groups = new ListTag(); data.put("JadeFluidStorage", groups);
        assertTrue(JadeFluidSnapshotReader.decode(data, raw -> { throw new AssertionError(); }).isEmpty());
        var group = new CompoundTag(); var views = new ListTag(); group.put("Views", views); groups.add(group);
        var view = new CompoundTag(); views.add(view);
        assertTrue(JadeFluidSnapshotReader.decode(data, raw -> { throw new AssertionError(); }).isEmpty());
        view.putLong("capacity", 12000); view.put("fluid", new CompoundTag());
        var empty = JadeFluidSnapshotReader.decode(data, raw -> new JadeFluidSnapshotReader.DecodedFluid(FluidStack.EMPTY, 0)).orElseThrow();
        assertEquals(0, empty.tanks().getFirst().amount());
        assertEquals(12000, empty.tanks().getFirst().capacity());
        var filled = JadeFluidSnapshotReader.decode(data, raw -> new JadeFluidSnapshotReader.DecodedFluid(new FluidStack(Fluids.WATER, 1), 2000)).orElseThrow();
        assertEquals(2000, filled.tanks().getFirst().amount());
        assertTrue(JadeFluidSnapshotReader.decode(data, raw -> { throw new IllegalArgumentException("codec mismatch"); }).isEmpty());
    }
}
