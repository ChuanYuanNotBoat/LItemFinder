package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.NamespacedId;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftBlockStateMetadataTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void retainsChestFacingAndHalfWithoutChangingTheRootIdentity() {
        ContainerRecord source = container(Map.of("minecraftBlock", "minecraft:chest"));
        var state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT);

        ContainerRecord saved = MinecraftBlockStateMetadata.withState(source, state);

        assertEquals(source.id(), saved.id());
        assertEquals(source.location(), saved.location());
        assertEquals("minecraft:chest", saved.metadata().get("minecraftBlock"));
        assertEquals(state, MinecraftBlockStateMetadata.restore(saved, Blocks.CHEST.defaultBlockState()));
    }

    @Test
    void legacyMalformedAndWrongBlockStatesUseTheExistingFallback() {
        var fallback = Blocks.CHEST.defaultBlockState();
        assertEquals(fallback, MinecraftBlockStateMetadata.restore(container(Map.of()), fallback));
        assertEquals(fallback, MinecraftBlockStateMetadata.restore(
                container(Map.of(MinecraftBlockStateMetadata.KEY, "minecraft:chest[type=unknown]")), fallback));
        assertEquals(fallback, MinecraftBlockStateMetadata.restore(
                container(Map.of(MinecraftBlockStateMetadata.KEY, "minecraft:barrel[facing=north]")), fallback));
    }

    private static ContainerRecord container(Map<String, String> metadata) {
        return new ContainerRecord(new ContainerId("block:test"), ContainerType.CHEST,
                new WorldLocation("test-world", NamespacedId.parse("minecraft:overworld"), 10, 64, 20), metadata);
    }
}
