package dev.litemfinder.neoforge.capture;

import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.neoforge.api.inventory.BlockInventory;
import dev.litemfinder.neoforge.identity.MinecraftContainerIdentityResolver;
import dev.litemfinder.neoforge.identity.MinecraftScopeResolver;
import dev.litemfinder.neoforge.mapping.CachedItemTagResolver;
import dev.litemfinder.neoforge.mapping.MenuSnapshotMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClientBlockInventoryCaptureCoordinatorTest {
    @BeforeAll
    static void bootstrapMinecraft() { Bootstrap.bootStrap(); }

    @Test
    void lookingAtDrawerIndexesFullCountAndTracksChangeAndEmptyWithoutAMenu() {
        var fixture = new Fixture();
        var position = new BlockPos(12, 64, 20);
        BlockEntity drawer = fixture.block(position, 1024);
        fixture.tick("world", position);
        assertEquals(1, fixture.captures.size());
        fixture.tick("world", position);
        assertEquals(1, fixture.captures.size(), "unchanged contents must not write each tick");
        fixture.contents.put(drawer, inventory(2048));
        fixture.tick("world", null);
        fixture.contents.put(drawer, inventory(0));
        fixture.tick("world", null);
        assertEquals(3, fixture.captures.size());

        var index = new InMemoryStorageIndex();
        var mapper = new MenuSnapshotMapper(new CachedItemTagResolver());
        for (int i = 0; i < fixture.captures.size(); i++) {
            var snapshot = mapper.map(fixture.captures.get(i),
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), Instant.EPOCH.plusSeconds(i));
            assertEquals(new WorldLocation("world", NamespacedId.parse("minecraft:overworld"), 12, 64, 20),
                    snapshot.container().location());
            assertEquals("minecraft:barrel", snapshot.container().metadata().get("minecraftBlock"));
            assertTrue(snapshot.container().metadata().containsKey("minecraftBlockState"));
            index.update(snapshot);
            assertEquals(1, index.rootContainerCount());
            if (i < 2) assertEquals(i == 0 ? 1024 : 2048, index.allEntries().getFirst().stack().count());
        }
        assertTrue(index.allEntries().isEmpty(), "empty drawer must replace old items");
    }

    @Test
    void unloadedChunksAreNeverReadAndTemporaryFailureDoesNotClearLastContents() {
        var fixture = new Fixture();
        BlockEntity drawer = fixture.block(BlockPos.ZERO, 1000);
        fixture.tick("world", BlockPos.ZERO);
        fixture.contents.remove(drawer);
        fixture.tick("world", null);
        assertEquals(1, fixture.captures.size());
        fixture.contents.put(drawer, inventory(2000));
        fixture.tick("world", null);
        assertEquals(2, fixture.captures.size(), "retry after unavailable must succeed");
        clearInvocations(fixture.level);
        when(fixture.level.hasChunk(anyInt(), anyInt())).thenReturn(false);
        fixture.tick("world", BlockPos.ZERO);
        verify(fixture.level, never()).getBlockEntity(any());
        assertEquals(2, fixture.captures.size());
        when(fixture.level.hasChunk(anyInt(), anyInt())).thenReturn(true);
        fixture.tick("world", null);
        assertEquals(2, fixture.captures.size(), "unload must drop background tracking");
    }

    @Test
    void scopeChangesAndReplacedBlockInstancesDoNotReuseOldObservation() {
        var fixture = new Fixture();
        fixture.block(BlockPos.ZERO, 1000);
        fixture.tick("first-world", BlockPos.ZERO);
        fixture.tick("second-world", null);
        assertEquals(1, fixture.captures.size(), "previous world must not keep polling");
        fixture.tick("second-world", BlockPos.ZERO);
        assertNotEquals(fixture.captures.get(0).identity().container().id(),
                fixture.captures.get(1).identity().container().id());
        fixture.block(BlockPos.ZERO, 1000);
        fixture.tick("second-world", null);
        assertEquals(3, fixture.captures.size(), "replacement must be observed despite matching contents");
    }

    @Test
    void discoveryAndBackgroundReadsHaveAFixedBudgetAndTrackingCapacity() {
        var fixture = new Fixture();
        for (int i = 0; i < 100; i++) {
            var position = new BlockPos(i, 64, 0);
            fixture.block(position, 1000);
            int before = fixture.reads.get();
            fixture.tick("world", position);
            assertTrue(fixture.reads.get() - before <= 2);
        }
        fixture.seen.clear();
        for (int i = 0; i < 200; i++) fixture.tick("world", null);
        assertEquals(ClientBlockInventoryCaptureCoordinator.MAX_TRACKED, fixture.seen.size());
        fixture.coordinator.reset();
        int before = fixture.reads.get();
        fixture.tick("world", null);
        assertEquals(before, fixture.reads.get());
    }

    @Test
    void registeredAdaptersHavePrecedenceAndFailuresDoNotBreakOtherReaders() throws Exception {
        BlockEntity owner = mock(BlockEntity.class);
        try (var registration = BlockInventoryAdapters.register(block -> Optional.of(inventory(1024)));
             var broken = BlockInventoryAdapters.register(block -> { throw new IllegalStateException("unavailable"); })) {
            assertEquals(1024, BlockInventoryAdapters.read(owner).orElseThrow().stacks().getFirst().getCount());
        }
        assertTrue(BlockInventoryAdapters.read(owner).isEmpty());
    }

    private static BlockInventory inventory(int count) {
        return new BlockInventory("fixture:drawer", List.of(new ItemStack(Items.DIRT, count)));
    }

    private static class Fixture {
        final Level level = mock(Level.class);
        final Map<BlockPos, BlockEntity> blocks = new HashMap<>();
        final Map<BlockEntity, BlockInventory> contents = new HashMap<>();
        final java.util.Set<BlockEntity> seen = new java.util.HashSet<>();
        final List<BlockCaptureRequest> captures = new ArrayList<>();
        final AtomicInteger reads = new AtomicInteger();
        final ClientBlockInventoryCaptureCoordinator coordinator;

        Fixture() {
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.isInWorldBounds(any())).thenReturn(true);
            when(level.hasChunk(anyInt(), anyInt())).thenReturn(true);
            when(level.getBlockEntity(any())).thenAnswer(call -> blocks.get(call.getArgument(0)));
            when(level.getBlockState(any())).thenReturn(Blocks.BARREL.defaultBlockState());
            coordinator = new ClientBlockInventoryCaptureCoordinator(new MinecraftScopeResolver(),
                    new MinecraftContainerIdentityResolver(), block -> {
                        reads.incrementAndGet();
                        seen.add(block);
                        return Optional.ofNullable(contents.get(block));
                    }, captures::add);
        }

        BlockEntity block(BlockPos position, int count) {
            BlockEntity block = mock(BlockEntity.class);
            blocks.put(position, block);
            contents.put(block, inventory(count));
            return block;
        }

        void tick(String scope, BlockPos target) { coordinator.tick(scope, level, target); }
    }
}
