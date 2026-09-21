package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.NamespacedId;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Copies a live Minecraft stack into immutable Core-owned values on the client thread. */
public final class MinecraftItemStackMapper {

    private final ComponentVariantFingerprint variants;

    public MinecraftItemStackMapper() {
        this(new ComponentVariantFingerprint());
    }

    MinecraftItemStackMapper(ComponentVariantFingerprint variants) {
        this.variants = Objects.requireNonNull(variants, "variants must not be null");
    }

    public Optional<MappedItemStack> map(ItemStack source, HolderLookup.Provider registries) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(registries, "registries must not be null");
        if (source.isEmpty()) {
            return Optional.empty();
        }

        NamespacedId itemId = MinecraftIds.toCore(BuiltInRegistries.ITEM.getKey(source.getItem()));
        String variant = variants.fingerprint(source, registries);
        ItemKey item = new ItemKey(itemId, variant);
        ItemStackInfo stack = new ItemStackInfo(item, source.getCount());
        Set<NamespacedId> tags = source.getTags()
                .map(TagKey<Item>::location)
                .map(MinecraftIds::toCore)
                .collect(Collectors.toCollection(TreeSet::new));
        return Optional.of(new MappedItemStack(stack, tags));
    }
}
