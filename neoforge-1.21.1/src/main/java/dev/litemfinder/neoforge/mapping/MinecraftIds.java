package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.NamespacedId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Converts Minecraft identifiers at the adapter boundary. */
public final class MinecraftIds {

    private MinecraftIds() {
    }

    public static NamespacedId toCore(ResourceLocation id) {
        Objects.requireNonNull(id, "id must not be null");
        return new NamespacedId(id.getNamespace(), id.getPath());
    }

    public static ResourceLocation fromCore(NamespacedId id) {
        Objects.requireNonNull(id, "id must not be null");
        return ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
    }
}
