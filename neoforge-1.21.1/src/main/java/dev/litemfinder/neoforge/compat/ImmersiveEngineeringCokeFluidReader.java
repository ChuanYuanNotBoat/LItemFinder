package dev.litemfinder.neoforge.compat;

import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.neoforge.api.inventory.BlockFluidInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Optional;

/** The coke oven state does not sync its tank. Use verified Jade data, canonicalized to its loaded master. */
public final class ImmersiveEngineeringCokeFluidReader {
    private ImmersiveEngineeringCokeFluidReader() { }
    public static Optional<BlockFluidInventory> read(BlockEntity owner) {
        try {
            var loader = ImmersiveEngineeringCokeFluidReader.class.getClassLoader();
            var multi = Class.forName("blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE", false, loader);
            if (!multi.isInstance(owner)) return Optional.empty();
            var helperType = Class.forName("blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockBEHelper", false, loader);
            Object helper = multi.getMethod("getHelper").invoke(owner);
            Object state = helperType.getMethod("getState").invoke(helper);
            if (!Class.forName("blusunrize.immersiveengineering.common.blocks.multiblocks.logic.CokeOvenLogic$State", false, loader)
                    .isInstance(state)) return Optional.empty();
            Object context = helperType.getMethod("getContext").invoke(helper);
            var contextType = Class.forName("blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext", false, loader);
            Object mbLevel = contextType.getMethod("getLevel").invoke(context);
            Object registration = helperType.getMethod("getMultiblock").invoke(helper);
            var registrationType = Class.forName("blusunrize.immersiveengineering.api.multiblocks.blocks.MultiblockRegistration", false, loader);
            BlockPos relativeMaster = (BlockPos) registrationType.getMethod("masterPosInMB").invoke(registration);
            var levelType = Class.forName("blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockLevel", false, loader);
            BlockPos master = (BlockPos) levelType.getMethod("toAbsolute", BlockPos.class).invoke(mbLevel, relativeMaster);
            if (owner.getLevel() == null || !owner.getLevel().hasChunk(master.getX() >> 4, master.getZ() >> 4)) return Optional.empty();
            return JadeFluidSnapshotReader.read(owner).filter(fluids -> fluids.tanks().size() == 1).map(fluids -> new BlockFluidInventory(master,
                    NamespacedId.parse("immersiveengineering:cokeoven"), 4, fluids));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return Optional.empty(); }
    }
}
