package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedItemTagResolverTest {

    @Test
    void servesImmutableTagsForEveryVariantOfRememberedItem() {
        CachedItemTagResolver resolver = new CachedItemTagResolver();
        ItemKey base = ItemKey.parse("minecraft:iron_ingot");
        NamespacedId tag = NamespacedId.parse("c:ingots/iron");
        resolver.remember(new MappedItemStack(new ItemStackInfo(base, 4), Set.of(tag)));

        assertEquals(Set.of(tag), resolver.tags(base.withVariant("components:v1:abc")));
        assertEquals(1, resolver.cachedItemCount());

        resolver.clear();

        assertTrue(resolver.tags(base).isEmpty());
    }
}
