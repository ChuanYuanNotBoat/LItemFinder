package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.BlockInventory;
import dev.litemfinder.neoforge.api.inventory.BlockInventoryAdapter;
import dev.litemfinder.neoforge.identity.MinecraftContainerIdentityResolver;
import dev.litemfinder.neoforge.identity.MinecraftScopeResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Consumer;

/** Discovers aimed-at storage and refreshes a bounded set, without scanning chunks or requesting data. */
public final class ClientBlockInventoryCaptureCoordinator {
    static final int MAX_TRACKED = 64;
    private final MinecraftScopeResolver scopes;
    private final MinecraftContainerIdentityResolver identities;
    private final BlockInventoryAdapter reader;
    private final Consumer<BlockCaptureRequest> sink;
    private final LinkedHashMap<BlockPos, Observation> tracked = new LinkedHashMap<>();
    private Level activeLevel;
    private String activeScope;
    private BlockPos interaction;
    private int ticks;

    public ClientBlockInventoryCaptureCoordinator(MinecraftScopeResolver scopes,
            MinecraftContainerIdentityResolver identities, Consumer<BlockCaptureRequest> sink) {
        this(scopes, identities, BlockInventoryAdapters::read, sink);
    }

    ClientBlockInventoryCaptureCoordinator(MinecraftScopeResolver scopes,
            MinecraftContainerIdentityResolver identities, BlockInventoryAdapter reader,
            Consumer<BlockCaptureRequest> sink) {
        this.scopes = Objects.requireNonNull(scopes);
        this.identities = Objects.requireNonNull(identities);
        this.reader = Objects.requireNonNull(reader);
        this.sink = Objects.requireNonNull(sink);
    }

    public void rememberBlock(BlockPos position) {
        interaction = position.immutable();
    }

    public void clientTick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        var scope = scopes.resolve(minecraft);
        if (scope.isEmpty()) return;
        BlockPos target = interaction;
        interaction = null;
        if (target == null && minecraft.screen == null && minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK && ticks % 5 == 0) {
            target = hit.getBlockPos();
        }
        tick(scope.orElseThrow(), minecraft.level, target);
    }

    void tick(String scope, Level level, BlockPos target) {
        if (activeLevel != level || !scope.equals(activeScope)) {
            reset();
            activeLevel = level;
            activeScope = scope;
        }
        ticks++;
        // One tracked block per tick, plus at most one explicit target. No world/chunk enumeration.
        BlockPos refresh = tracked.isEmpty() ? null : tracked.firstEntry().getKey();
        if (target != null) observe(level, scope, target);
        if (refresh != null && !refresh.equals(target)) observe(level, scope, refresh);
    }

    public void reset() {
        tracked.clear();
        activeLevel = null;
        activeScope = null;
        interaction = null;
        ticks = 0;
    }

    private void observe(Level level, String scope, BlockPos position) {
        Observation previous = tracked.remove(position);
        // Never let getBlockEntity cause a chunk load. Unload keeps the persisted snapshot intact.
        if (!level.isInWorldBounds(position) || !level.hasChunk(SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getZ()))) return;
        BlockEntity owner = level.getBlockEntity(position);
        if (owner == null || owner.isRemoved()) return;
        var inventory = reader.read(owner);
        if (inventory.isEmpty()) {
            if (previous != null && previous.owner() == owner) tracked.put(position.immutable(), previous);
            return;
        }
        BlockInventory contents = inventory.orElseThrow();
        BlockState state = level.getBlockState(position);
        if (previous == null || previous.owner() != owner || !previous.state().equals(state)
                || !contents.sameContents(previous.contents())) {
            sink.accept(new BlockCaptureRequest(identities.blockInventory(scope, level, position), contents));
        }
        tracked.put(position.immutable(), new Observation(owner, state, contents));
        if (tracked.size() > MAX_TRACKED) tracked.pollFirstEntry();
    }

    private record Observation(BlockEntity owner, BlockState state, BlockInventory contents) { }
}
