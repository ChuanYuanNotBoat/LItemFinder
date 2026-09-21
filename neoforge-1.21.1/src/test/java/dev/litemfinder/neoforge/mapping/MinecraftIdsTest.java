package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.NamespacedId;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftIdsTest {

    @Test
    void convertsBothDirectionsWithoutStringParsing() {
        ResourceLocation minecraft = ResourceLocation.fromNamespaceAndPath("example", "nested/item");

        NamespacedId core = MinecraftIds.toCore(minecraft);

        assertEquals(new NamespacedId("example", "nested/item"), core);
        assertEquals(minecraft, MinecraftIds.fromCore(core));
    }
}
