package dev.litemfinder.neoforge.lifecycle;

import com.mojang.logging.LogUtils;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.neoforge.diagnostics.CaptureDiagnostics;
import dev.litemfinder.neoforge.identity.MinecraftScopeResolver;
import dev.litemfinder.neoforge.mapping.MinecraftIds;
import dev.litemfinder.neoforge.persistence.SnapshotStorageCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Removes indexed block containers after the loaded client world confirms they no longer exist. */
public final class BlockContainerRemovalMonitor {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final int CONFIRM_REMOVAL_TICKS = 20;

    private final SnapshotStorageCoordinator storage;
    private final MinecraftScopeResolver scopes;
    private final CaptureDiagnostics diagnostics;
    private final Map<ContainerId, Observation> observations = new HashMap<>();

    private int ticksUntilCheck;
    private long ticks;
    private Level activeLevel;
    private String activeScope;

    public BlockContainerRemovalMonitor(
            SnapshotStorageCoordinator storage,
            MinecraftScopeResolver scopes,
            CaptureDiagnostics diagnostics
    ) {
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.scopes = Objects.requireNonNull(scopes, "scopes must not be null");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    }

    public void clientTick(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft must not be null");
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        Optional<String> currentScope = scopes.resolve(minecraft);
        if (currentScope.isEmpty()) {
            return;
        }
        tick(currentScope.orElseThrow(), minecraft.level);
    }

    void tick(String scope, Level level) {
        if (activeLevel != level || !scope.equals(activeScope)) {
            reset();
            activeLevel = level;
            activeScope = scope;
        }
        ticks++;
        if (ticksUntilCheck-- > 0) return;
        ticksUntilCheck = CHECK_INTERVAL_TICKS - 1;
        NamespacedId currentDimension = MinecraftIds.toCore(level.dimension().location());
        var currentIds = new HashSet<ContainerId>();
        for (InventorySnapshot snapshot : storage.currentIndex().rootSnapshots()) {
            if (!(snapshot.container().location() instanceof WorldLocation location)
                    || !location.scope().equals(scope)
                    || !location.dimension().equals(currentDimension)) {
                continue;
            }
            ContainerId id = snapshot.container().id();
            currentIds.add(id);
            BlockPos position = new BlockPos(location.x(), location.y(), location.z());
            if (!level.isInWorldBounds(position)) {
                forget(id);
                continue;
            }
            // hasChunk alone does not establish that this client's full block data is available.
            var access = level.getChunk(SectionPos.blockToSectionCoord(position.getX()),
                    SectionPos.blockToSectionCoord(position.getZ()), ChunkStatus.FULL, false);
            if (!(access instanceof LevelChunk chunk) || access instanceof EmptyLevelChunk) {
                forget(id);
                continue;
            }
            BlockState state = level.getBlockState(position);
            String expectedBlock = snapshot.container().metadata().get("minecraftBlock");
            // A legacy/recovered record with no known native identity cannot prove a later replacement.
            // Keep it until a fresh inventory observation supplies that identity.
            if (expectedBlock == null) {
                forget(id);
                continue;
            }
            if (stillMatches(snapshot, state)) {
                forget(id);
                observations.put(id, new Observation(chunk, expectedBlock));
                continue;
            }
            Observation observation = observations.get(id);
            // Restored records are historical. Initial air/replacements are not evidence of destruction.
            // Require a positive sighting in this exact chunk instance before accepting a later change.
            if (observation == null || observation.chunk != chunk
                    || !Objects.equals(observation.expectedBlock, expectedBlock)) {
                forget(id);
                continue;
            }
            if (!state.equals(observation.missingState)) {
                observation.missingState = state;
                observation.missingSince = ticks;
            } else if (ticks - observation.missingSince >= CONFIRM_REMOVAL_TICKS && !observation.queued) {
                queueRemoval(snapshot, observation);
            }
        }
        observations.keySet().removeIf(id -> {
            if (currentIds.contains(id)) return false;
            observations.get(id).valid.set(false);
            return true;
        });
    }

    public void reset() {
        ticksUntilCheck = 0;
        ticks = 0;
        activeLevel = null;
        activeScope = null;
        observations.values().forEach(observation -> observation.valid.set(false));
        observations.clear();
    }

    static boolean stillMatches(InventorySnapshot snapshot, BlockState state) {
        String expectedBlock = snapshot.container().metadata().get("minecraftBlock");
        if (expectedBlock != null) {
            NamespacedId actual = MinecraftIds.toCore(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            try {
                return actual.equals(NamespacedId.parse(expectedBlock));
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        // Legacy records must also be positively seen before a later mismatch can remove them.
        return !state.isAir() && state.hasBlockEntity();
    }

    private void forget(ContainerId id) {
        Observation previous = observations.remove(id);
        if (previous != null) previous.valid.set(false);
    }

    private void queueRemoval(InventorySnapshot snapshot, Observation observation) {
        ContainerId containerId = snapshot.container().id();
        observation.queued = true;
        storage.removeCurrentScopeContainer(snapshot, observation.valid::get).whenComplete((removed, failure) -> {
            observation.queued = false;
            if (failure != null) {
                LOGGER.warn("Failed to remove destroyed indexed container {}", containerId, failure);
            } else if (Boolean.TRUE.equals(removed)) {
                diagnostics.removed("block_missing_or_replaced");
                LOGGER.debug("Removed destroyed indexed container {}", containerId);
            }
        });
    }

    private static final class Observation {
        final LevelChunk chunk;
        final String expectedBlock;
        final AtomicBoolean valid = new AtomicBoolean(true);
        BlockState missingState;
        long missingSince;
        volatile boolean queued;
        Observation(LevelChunk chunk, String expectedBlock) { this.chunk = chunk; this.expectedBlock = expectedBlock; }
    }
}
