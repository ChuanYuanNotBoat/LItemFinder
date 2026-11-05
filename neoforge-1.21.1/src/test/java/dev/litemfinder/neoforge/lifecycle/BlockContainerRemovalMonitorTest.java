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
import org.junit.jupiter.api.io.TempDir;
import dev.litemfinder.neoforge.persistence.SnapshotStorageCoordinator;
import dev.litemfinder.neoforge.identity.ScopeIdFactory;
import dev.litemfinder.neoforge.identity.MinecraftScopeResolver;
import dev.litemfinder.neoforge.diagnostics.CaptureDiagnostics;
import dev.litemfinder.storage.sqlite.SqliteSnapshotRepository;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.nio.file.Path;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

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

    @Test
    void initialAirCannotDeleteRestoredContainersEvenAfterTheConfirmationDelay(@TempDir Path directory) {
        try (var fixture = new Fixture(directory)) {
            for (int index = 0; index < 150; index++) fixture.add("restored-" + index);
            fixture.storage.flush();
            fixture.checks(12);
            assertEquals(150, fixture.storage.currentIndex().rootContainerCount());
            try (var repository = new SqliteSnapshotRepository(directory.resolve(fixture.scope.substring("scope:v1:".length()) + ".db"))) {
                assertEquals(150, repository.findAll().size(), "restored records must survive in SQLite too");
            }
        }
    }

    @Test
    void aConfirmedBlockRequiresASustainedChangeAndRecoversFromTransientAir(@TempDir Path directory) {
        try (var fixture = new Fixture(directory)) {
            fixture.add("chest"); fixture.storage.flush();
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.CHEST.defaultBlockState()); fixture.checks(1);
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState()); fixture.checks(2);
            assertEquals(1, fixture.storage.currentIndex().rootContainerCount());
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.CHEST.defaultBlockState()); fixture.checks(1);
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState()); fixture.checks(2);
            assertEquals(1, fixture.storage.currentIndex().rootContainerCount());
            fixture.checks(1);
            assertEquals(0, fixture.storage.currentIndex().rootContainerCount());
        }
    }

    @Test
    void unloadingOrReplacingAChunkRequiresANewPositiveSighting(@TempDir Path directory) {
        try (var fixture = new Fixture(directory)) {
            fixture.add("chest"); fixture.storage.flush();
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.CHEST.defaultBlockState()); fixture.checks(1);
            when(fixture.level.getChunk(anyInt(), anyInt(), eq(ChunkStatus.FULL), eq(false))).thenReturn(null);
            fixture.checks(1);
            clearInvocations(fixture.level);
            fixture.checks(1);
            verify(fixture.level, never()).getBlockState(any());
            when(fixture.level.getChunk(anyInt(), anyInt(), eq(ChunkStatus.FULL), eq(false))).thenReturn(mock(LevelChunk.class));
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState()); fixture.checks(4);
            assertEquals(1, fixture.storage.currentIndex().rootContainerCount());
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.CHEST.defaultBlockState()); fixture.checks(1);
            when(fixture.level.getChunk(anyInt(), anyInt(), eq(ChunkStatus.FULL), eq(false))).thenReturn(mock(LevelChunk.class));
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState()); fixture.checks(4);
            assertEquals(1, fixture.storage.currentIndex().rootContainerCount());
        }
    }

    @Test
    void resettingTheWorldRevokesEarlierPresenceEvidence(@TempDir Path directory) {
        try (var fixture = new Fixture(directory)) {
            fixture.add("chest"); fixture.storage.flush();
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.CHEST.defaultBlockState()); fixture.checks(1);
            fixture.monitor.reset();
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState()); fixture.checks(4);
            assertEquals(1, fixture.storage.currentIndex().rootContainerCount());
        }
    }

    @Test
    void unknownNativeIdentityCannotBeDeletedAfterSeeingAnUnrelatedBlock(@TempDir Path directory) {
        try (var fixture = new Fixture(directory)) {
            var record = new ContainerRecord(new ContainerId("legacy"), ContainerType.CHEST,
                    new WorldLocation(fixture.scope, NamespacedId.parse("minecraft:overworld"), 1, 64, 3));
            fixture.storage.submit(new InventorySnapshot(record, 27, Instant.EPOCH, List.of()), true);
            fixture.storage.flush();
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.CHEST.defaultBlockState()); fixture.checks(1);
            when(fixture.level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState()); fixture.checks(5);
            assertEquals(1, fixture.storage.currentIndex().rootContainerCount());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final String scope = ScopeIdFactory.singleplayer("removal-login-regression");
        final Level level = mock(Level.class);
        final SnapshotStorageCoordinator storage;
        final BlockContainerRemovalMonitor monitor;
        Fixture(Path directory) {
            storage = new SnapshotStorageCoordinator(directory, 256);
            monitor = new BlockContainerRemovalMonitor(storage, new MinecraftScopeResolver(), new CaptureDiagnostics());
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.isInWorldBounds(any())).thenReturn(true);
            when(level.getChunk(anyInt(), anyInt(), eq(ChunkStatus.FULL), eq(false))).thenReturn(mock(LevelChunk.class));
            when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        }
        void add(String id) {
            var root = new ContainerRecord(new ContainerId(id), ContainerType.CHEST,
                    new WorldLocation(scope, NamespacedId.parse("minecraft:overworld"), 1, 64, 3),
                    Map.of("minecraftBlock", "minecraft:chest"));
            storage.submit(new InventorySnapshot(root, 27, Instant.EPOCH, List.of()), true);
        }
        void checks(int count) { for (int tick = 0; tick < count * 10; tick++) monitor.tick(scope, level); storage.flush(); }
        @Override public void close() { monitor.reset(); storage.close(); }
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
