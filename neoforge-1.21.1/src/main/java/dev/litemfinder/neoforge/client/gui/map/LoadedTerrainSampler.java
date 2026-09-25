package dev.litemfinder.neoforge.client.gui.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded client-thread cache for terrain that the server has already sent to the client. */
public final class LoadedTerrainSampler {

    private static final int MAX_CACHED_SAMPLES = 16_384;
    private static final int REFRESH_TICKS = 100;

    private final Map<SampleKey, CachedSample> samples = new LinkedHashMap<>(256, 0.75F, true);
    private String dimension = "";
    private int remainingNewSamples;

    public void beginFrame(int newSampleBudget) {
        remainingNewSamples = Math.max(0, newSampleBudget);
    }

    public Optional<TerrainSample> sample(ClientLevel level, int x, int z, int tick) {
        Objects.requireNonNull(level, "level must not be null");
        String currentDimension = level.dimension().location().toString();
        if (!currentDimension.equals(dimension)) {
            samples.clear();
            dimension = currentDimension;
        }

        SampleKey key = new SampleKey(x, z);
        CachedSample cached = samples.get(key);
        if (cached != null && tick < cached.refreshAtTick()) {
            return Optional.of(cached.sample());
        }
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            samples.remove(key);
            return Optional.empty();
        }
        if (remainingNewSamples <= 0) {
            return cached == null ? Optional.empty() : Optional.of(cached.sample());
        }
        remainingNewSamples--;

        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int surfaceHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos surface = new BlockPos(x, Math.max(level.getMinBuildHeight(), surfaceHeight), z);
        FluidState fluid = level.getFluidState(surface);
        SurfaceType surfaceType = fluid.is(FluidTags.WATER) ? SurfaceType.WATER
                : fluid.is(FluidTags.LAVA) ? SurfaceType.LAVA : SurfaceType.LAND;
        TerrainSample sample = new TerrainSample(Math.max(level.getMinBuildHeight(), height), surfaceType);
        samples.put(key, new CachedSample(sample, tick + REFRESH_TICKS));
        trim();
        return Optional.of(sample);
    }

    public void clear() {
        samples.clear();
        dimension = "";
        remainingNewSamples = 0;
    }

    private void trim() {
        while (samples.size() > MAX_CACHED_SAMPLES) {
            samples.remove(samples.keySet().iterator().next());
        }
    }

    public enum SurfaceType {
        LAND,
        WATER,
        LAVA
    }

    public record TerrainSample(int height, SurfaceType surface) {

        public TerrainSample {
            Objects.requireNonNull(surface, "surface must not be null");
        }
    }

    private record SampleKey(int x, int z) {
    }

    private record CachedSample(TerrainSample sample, int refreshAtTick) {
    }
}
