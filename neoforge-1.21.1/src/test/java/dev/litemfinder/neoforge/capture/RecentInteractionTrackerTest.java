package dev.litemfinder.neoforge.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentInteractionTrackerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void eitherHalfOfDoubleChestNormalizesToSamePosition() {
        BlockPos first = new BlockPos(10, 64, 10);
        BlockState left = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockPos second = first.relative(ChestBlock.getConnectedDirection(left));
        BlockState right = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT);

        BlockPos fromFirst = RecentInteractionTracker.normalizeChest(first, left);
        BlockPos fromSecond = RecentInteractionTracker.normalizeChest(second, right);

        assertEquals(fromFirst, fromSecond);
    }
}
