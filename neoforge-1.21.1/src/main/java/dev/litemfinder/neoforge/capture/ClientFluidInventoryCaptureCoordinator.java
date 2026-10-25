package dev.litemfinder.neoforge.capture;

import dev.litemfinder.core.model.*;
import dev.litemfinder.neoforge.api.inventory.BlockFluidInventory;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import dev.litemfinder.neoforge.identity.MinecraftScopeResolver;
import dev.litemfinder.neoforge.mapping.FluidInventoryMetadata;
import dev.litemfinder.neoforge.mapping.MinecraftBlockStateMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** At most one aimed target per five ticks. Failed/absent reads never emit or replace prior records. */
public final class ClientFluidInventoryCaptureCoordinator {
    private final MinecraftScopeResolver scopes = new MinecraftScopeResolver();
    private final Function<BlockEntity, Optional<BlockFluidInventory>> reader;
    private final Consumer<InventorySnapshot> sink;
    private final Map<ContainerId, Long> last = new LinkedHashMap<>();
    private Level level;
    private String scope;
    private int ticks;
    public ClientFluidInventoryCaptureCoordinator(Consumer<InventorySnapshot> sink) { this(FluidInventoryAdapters::readBlock, sink); }
    ClientFluidInventoryCaptureCoordinator(Function<BlockEntity, Optional<BlockFluidInventory>> reader, Consumer<InventorySnapshot> sink) {
        this.reader = reader; this.sink = sink;
    }
    public void clientTick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) { reset(); return; }
        if (ticks++ % 5 != 0 || minecraft.screen != null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;
        scopes.resolve(minecraft).ifPresent(scope -> observe(scope, minecraft.level, hit.getBlockPos()));
    }
    void observe(String scope, Level level, BlockPos position) {
        if (this.level != level || !scope.equals(this.scope)) { reset(); this.level = level; this.scope = scope; }
        if (!level.hasChunk(position.getX() >> 4, position.getZ() >> 4)) return;
        BlockEntity owner = level.getBlockEntity(position);
        if (owner == null || owner.isRemoved()) return;
        Optional<BlockFluidInventory> result;
        try { result = reader.apply(owner); } catch (RuntimeException | LinkageError failure) { return; }
        if (result.isEmpty()) return;
        var read = result.orElseThrow();
        var canonical = read.position();
        if (!level.hasChunk(canonical.getX() >> 4, canonical.getZ() >> 4)) return;
        var canonicalOwner = level.getBlockEntity(canonical);
        if (canonicalOwner == null || canonicalOwner.isRemoved()) return;
        var state = level.getBlockState(canonical);
        var identity = ContainerIdentityFactory.block(scope, NamespacedId.parse(level.dimension().location().toString()),
                canonical.getX(), canonical.getY(), canonical.getZ(), read.containerType(),
                NamespacedId.parse(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        long signature = 31 * read.fluids().fingerprint() + state.hashCode() + System.identityHashCode(canonicalOwner);
        if (Objects.equals(last.get(identity.container().id()), signature)) return;
        var root = MinecraftBlockStateMetadata.withState(identity.container(), state);
        var metadata = new TreeMap<>(root.metadata());
        FluidInventoryMetadata.write(metadata, read.fluids(), level.registryAccess(), Instant.now());
        sink.accept(new InventorySnapshot(new ContainerRecord(root.id(), root.type(), root.location(), metadata),
                read.itemSlots(), Instant.now(), List.of()));
        last.put(root.id(), signature);
        if (last.size() > 64) last.remove(last.keySet().iterator().next());
    }
    public void reset() { last.clear(); level = null; scope = null; ticks = 0; }
}
