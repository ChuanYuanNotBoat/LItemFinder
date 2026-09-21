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
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Removes indexed block containers after the loaded client world confirms they no longer exist. */
public final class BlockContainerRemovalMonitor {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL_TICKS = 10;

    private final SnapshotStorageCoordinator storage;
    private final MinecraftScopeResolver scopes;
    private final CaptureDiagnostics diagnostics;
    private final Set<ContainerId> pendingRemovals = ConcurrentHashMap.newKeySet();

    private int ticksUntilCheck;

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
        if (ticksUntilCheck-- > 0) {
            return;
        }
        ticksUntilCheck = CHECK_INTERVAL_TICKS - 1;

        Optional<String> currentScope = scopes.resolve(minecraft);
        if (currentScope.isEmpty()) {
            return;
        }
        NamespacedId currentDimension = MinecraftIds.toCore(minecraft.level.dimension().location());
        for (InventorySnapshot snapshot : storage.currentIndex().rootSnapshots()) {
            if (!(snapshot.container().location() instanceof WorldLocation location)
                    || !location.scope().equals(currentScope.orElseThrow())
                    || !location.dimension().equals(currentDimension)) {
                continue;
            }
            BlockPos position = new BlockPos(location.x(), location.y(), location.z());
            if (!minecraft.level.isInWorldBounds(position)
                    || !minecraft.level.hasChunk(
                    SectionPos.blockToSectionCoord(position.getX()),
                    SectionPos.blockToSectionCoord(position.getZ())
            )) {
                continue;
            }
            BlockState state = minecraft.level.getBlockState(position);
            if (stillMatches(snapshot, state)) {
                continue;
            }
            queueRemoval(snapshot);
        }
    }

    public void reset() {
        ticksUntilCheck = 0;
        pendingRemovals.clear();
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

        // Legacy snapshots did not persist the block ID. Air and non-menu replacements are still safe
        // to remove; a surviving menu block is retained until it is observed again with new metadata.
        return !state.isAir() && state.hasBlockEntity();
    }

    private void queueRemoval(InventorySnapshot snapshot) {
        ContainerId containerId = snapshot.container().id();
        if (!pendingRemovals.add(containerId)) {
            return;
        }
        storage.removeCurrentScopeContainer(snapshot).whenComplete((removed, failure) -> {
            pendingRemovals.remove(containerId);
            if (failure != null) {
                LOGGER.warn("Failed to remove destroyed indexed container {}", containerId, failure);
            } else if (Boolean.TRUE.equals(removed)) {
                diagnostics.removed("block_missing_or_replaced");
                LOGGER.debug("Removed destroyed indexed container {}", containerId);
            }
        });
    }
}
