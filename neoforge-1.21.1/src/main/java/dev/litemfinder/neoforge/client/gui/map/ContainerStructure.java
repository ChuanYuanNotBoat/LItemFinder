package dev.litemfinder.neoforge.client.gui.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Geometry shared by rendering, outlines and hit testing for a single indexed root. */
public record ContainerStructure(List<Part> parts, AABB bounds) {

    public ContainerStructure {
        parts = List.copyOf(parts);
    }

    public static ContainerStructure from(BlockState root) {
        List<Part> parts = List.of(new Part(BlockPos.ZERO, root));
        if (root.getBlock() instanceof ChestBlock && root.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = BlockPos.ZERO.relative(ChestBlock.getConnectedDirection(root));
            ChestType otherType = root.getValue(ChestBlock.TYPE) == ChestType.LEFT ? ChestType.RIGHT : ChestType.LEFT;
            parts = List.of(parts.getFirst(), new Part(other, root.setValue(ChestBlock.TYPE, otherType)));
        }
        AABB bounds = new AABB(BlockPos.ZERO);
        for (Part part : parts) {
            bounds = bounds.minmax(new AABB(part.offset()));
        }
        return new ContainerStructure(parts, bounds);
    }

    public record Part(BlockPos offset, BlockState state) {
    }
}
