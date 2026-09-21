package dev.litemfinder.neoforge.lifecycle;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.WorldLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockContainerRemovalMonitorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void detectsMissingOrReplacedExpectedBlock() {
        InventorySnapshot trappedChest = snapshot(Map.of("minecraftBlock", "minecraft:trapped_chest"));

        assertTrue(BlockContainerRemovalMonitor.stillMatches(
                trappedChest,
                Blocks.TRAPPED_CHEST.defaultBlockState()
        ));
        assertFalse(BlockContainerRemovalMonitor.stillMatches(trappedChest, Blocks.CHEST.defaultBlockState()));
        assertFalse(BlockContainerRemovalMonitor.stillMatches(trappedChest, Blocks.AIR.defaultBlockState()));
    }

    @Test
    void legacySnapshotsAreOnlyRetainedForExistingBlockEntities() {
        InventorySnapshot legacy = snapshot(Map.of());

        assertTrue(BlockContainerRemovalMonitor.stillMatches(legacy, Blocks.CHEST.defaultBlockState()));
        assertFalse(BlockContainerRemovalMonitor.stillMatches(legacy, Blocks.STONE.defaultBlockState()));
        assertFalse(BlockContainerRemovalMonitor.stillMatches(legacy, Blocks.AIR.defaultBlockState()));
    }

    private static InventorySnapshot snapshot(Map<String, String> metadata) {
        ContainerRecord container = new ContainerRecord(
                new ContainerId("block:test"),
                ContainerType.CHEST,
                new WorldLocation(
                        "scope:v1:test",
                        new NamespacedId("minecraft", "overworld"),
                        1,
                        2,
                        3
                ),
                metadata
        );
        return new InventorySnapshot(container, 27, Instant.EPOCH, List.of());
    }
}
