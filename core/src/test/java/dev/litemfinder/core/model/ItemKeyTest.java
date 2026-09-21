package dev.litemfinder.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemKeyTest {

    @Test
    void parsesNamespacedItemId() {
        ItemKey key = ItemKey.parse("create:copper_sheet");

        assertEquals("create", key.namespace());
        assertEquals("copper_sheet", key.id());
        assertFalse(key.hasVariant());
        assertEquals("create:copper_sheet", key.toString());
    }

    @Test
    void keepsVariantOpaqueAndPartOfIdentity() {
        ItemKey base = ItemKey.parse("minecraft:enchanted_book");
        ItemKey sharpness = base.withVariant("enchantment=minecraft:sharpness;level=5");

        assertTrue(sharpness.hasVariant());
        assertEquals("enchantment=minecraft:sharpness;level=5", sharpness.variantValue().orElseThrow());
        assertFalse(base.equals(sharpness));
    }

    @Test
    void rejectsAmbiguousOrNonCanonicalIds() {
        assertThrows(IllegalArgumentException.class, () -> ItemKey.parse("diamond"));
        assertThrows(IllegalArgumentException.class, () -> ItemKey.parse("Minecraft:diamond"));
        assertThrows(IllegalArgumentException.class, () -> ItemKey.parse("minecraft:diamond ore"));
    }
}
