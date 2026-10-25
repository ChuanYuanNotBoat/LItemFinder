package dev.litemfinder.neoforge.client.gui.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContainerStructureTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void doubleChestsHaveTwoComplementaryHalvesInEveryFacing() {
        Map<Direction, BlockPos> leftOffsets = Map.of(
                Direction.NORTH, new BlockPos(1, 0, 0),
                Direction.EAST, new BlockPos(0, 0, 1),
                Direction.SOUTH, new BlockPos(-1, 0, 0),
                Direction.WEST, new BlockPos(0, 0, -1)
        );
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.CHEST, Blocks.TRAPPED_CHEST}) {
            for (var facing : leftOffsets.entrySet()) {
                for (ChestType half : new ChestType[]{ChestType.LEFT, ChestType.RIGHT}) {
                    BlockState state = block.defaultBlockState().setValue(ChestBlock.FACING, facing.getKey())
                            .setValue(ChestBlock.TYPE, half);
                    var structure = ContainerStructure.from(state);
                    BlockPos expectedOffset = half == ChestType.LEFT
                            ? facing.getValue() : facing.getValue().multiply(-1);

                    assertEquals(2, structure.parts().size());
                    assertEquals(BlockPos.ZERO, structure.parts().getFirst().offset());
                    assertEquals(expectedOffset, structure.parts().getLast().offset());
                    assertEquals(facing.getKey(), structure.parts().getLast().state().getValue(ChestBlock.FACING));
                    assertEquals(half == ChestType.LEFT ? ChestType.RIGHT : ChestType.LEFT,
                            structure.parts().getLast().state().getValue(ChestBlock.TYPE));
                    assertEquals(2.0, structure.bounds().getXsize() * structure.bounds().getYsize()
                            * structure.bounds().getZsize());
                    assertTrue(structure.bounds().contains(expectedOffset.getX() + 0.5,
                            0.5, expectedOffset.getZ() + 0.5));
                }
            }
        }
    }

    @Test
    void boundsIncludeTheSecondBlockAlongEitherWorldAxis() {
        var eastWest = ContainerStructure.from(Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        var northSouth = ContainerStructure.from(Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.WEST).setValue(ChestBlock.TYPE, ChestType.LEFT));

        assertEquals(new AABB(0, 0, 0, 2, 1, 1), eastWest.bounds());
        assertEquals(new AABB(0, 0, -1, 1, 1, 1), northSouth.bounds());
    }

    @Test
    void singleContainersAreNotExpandedBasedOnInventoryCapacity() {
        for (BlockState state : new BlockState[]{Blocks.CHEST.defaultBlockState(), Blocks.BARREL.defaultBlockState(),
                Blocks.ENDER_CHEST.defaultBlockState()}) {
            var structure = ContainerStructure.from(state);
            assertEquals(1, structure.parts().size());
            assertEquals(new AABB(0, 0, 0, 1, 1, 1), structure.bounds());
        }
    }
}
