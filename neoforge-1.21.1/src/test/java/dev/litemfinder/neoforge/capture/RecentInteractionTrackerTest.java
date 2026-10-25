package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.identity.MinecraftContainerIdentityResolver;
import dev.litemfinder.neoforge.mapping.MinecraftBlockStateMetadata;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

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

    @Test
    void recordsTheSameCanonicalHalfStateWhenEitherHalfIsOpened() {
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            BlockPos first = new BlockPos(10, 64, 10);
            BlockState left = Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.LEFT);
            BlockPos second = first.relative(ChestBlock.getConnectedDirection(left));
            BlockState right = left.setValue(ChestBlock.TYPE, ChestType.RIGHT);
            Level level = mock(Level.class);
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.getBlockState(first)).thenReturn(left);
            when(level.getBlockState(second)).thenReturn(right);
            Player player = mock(Player.class);
            when(player.level()).thenReturn(level);
            ChestMenu menu = ChestMenu.sixRows(1, new Inventory(null));
            var tracker = new RecentInteractionTracker(new MinecraftContainerIdentityResolver());

            tracker.rememberBlock(level, first);
            var fromLeft = tracker.resolve("test-world", player, menu);
            tracker.rememberBlock(level, second);
            var fromRight = tracker.resolve("test-world", player, menu);

            assertEquals(fromLeft, fromRight);
            var canonical = RecentInteractionTracker.normalizeChest(first, left);
            assertEquals(level.getBlockState(canonical), MinecraftBlockStateMetadata.restore(
                    fromLeft.container(), Blocks.CHEST.defaultBlockState()));
        }
    }
}
