package dev.litemfinder.neoforge.client.gui.map;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.neoforge.client.view.ContainerOverview;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Resolves recorded container metadata into client-only Minecraft block visuals. */
public final class ContainerVisualResolver {

    private final Map<String, StaticVisual> cache = new HashMap<>();

    public ContainerBlockVisual resolve(ContainerOverview.RootRow row) {
        String recordedId = row.container().metadata().get("minecraftBlock");
        StaticVisual base = staticVisual(recordedId);
        BlockState state = liveState(row, base.block(), recordedId);
        return new ContainerBlockVisual(base.block(), state, base.icon(), base.resolved());
    }

    public void clear() {
        cache.clear();
    }

    private StaticVisual staticVisual(String blockId) {
        String key = blockId == null || blockId.isBlank() ? "<fallback>" : blockId;
        return cache.computeIfAbsent(key, ignored -> resolveStatic(blockId));
    }

    private StaticVisual resolveStatic(String blockId) {
        if (blockId != null && !blockId.isBlank()) {
            try {
                ResourceLocation key = ResourceLocation.parse(blockId);
                Block block = BuiltInRegistries.BLOCK.get(key);
                if (block != Blocks.AIR || blockId.equals("minecraft:air")) {
                    return new StaticVisual(block, new ItemStack(block), true);
                }
            } catch (RuntimeException ignored) {
                // Old/corrupt metadata should degrade to a generic visual instead of breaking the map.
            }
        }
        return new StaticVisual(Blocks.CHEST, new ItemStack(Blocks.CHEST), false);
    }

    private BlockState liveState(ContainerOverview.RootRow row, Block fallback, String recordedId) {
        if (!(row.container().location() instanceof WorldLocation world)) {
            return fallback.defaultBlockState();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().location().toString().equals(world.dimension().toString())) {
            return fallback.defaultBlockState();
        }
        BlockPos pos = new BlockPos(world.x(), world.y(), world.z());
        if (!minecraft.level.hasChunkAt(pos)) {
            return fallback.defaultBlockState();
        }
        BlockState live = minecraft.level.getBlockState(pos);
        ResourceLocation liveKey = BuiltInRegistries.BLOCK.getKey(live.getBlock());
        if (recordedId == null || liveKey.toString().equals(recordedId)) {
            return live;
        }
        return fallback.defaultBlockState();
    }

    public record ContainerBlockVisual(Block block, BlockState state, ItemStack icon, boolean resolved) {
    }

    private record StaticVisual(Block block, ItemStack icon, boolean resolved) {
    }
}
