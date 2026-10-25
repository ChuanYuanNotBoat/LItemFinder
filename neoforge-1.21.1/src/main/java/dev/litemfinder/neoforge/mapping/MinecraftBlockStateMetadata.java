package dev.litemfinder.neoforge.mapping;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.litemfinder.core.model.ContainerRecord;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.TreeMap;

/** Persists observed block properties without introducing Minecraft types into Core. */
public final class MinecraftBlockStateMetadata {

    public static final String KEY = "minecraftBlockState";

    private MinecraftBlockStateMetadata() {
    }

    public static ContainerRecord withState(ContainerRecord container, BlockState state) {
        var metadata = new TreeMap<>(container.metadata());
        metadata.put(KEY, BlockStateParser.serialize(state));
        return new ContainerRecord(container.id(), container.type(), container.location(), metadata);
    }

    public static BlockState restore(ContainerRecord container, BlockState fallback) {
        String encoded = container.metadata().get(KEY);
        if (encoded != null) {
            try {
                BlockState state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), encoded, false)
                        .blockState();
                if (state.is(fallback.getBlock())) {
                    return state;
                }
            } catch (CommandSyntaxException | IllegalArgumentException ignored) {
                // Legacy or incompatible metadata keeps the existing generic visual.
            }
        }
        return fallback;
    }
}
