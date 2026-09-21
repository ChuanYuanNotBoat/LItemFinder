package dev.litemfinder.neoforge.mapping;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftItemStackMapperTest {

    private final MinecraftItemStackMapper mapper = new MinecraftItemStackMapper();

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void ignoresEmptyStacks() {
        assertTrue(mapper.map(ItemStack.EMPTY, registries()).isEmpty());
    }

    @Test
    void countDoesNotAffectVariant() {
        var one = mapper.map(new ItemStack(Items.STONE, 1), registries()).orElseThrow();
        var many = mapper.map(new ItemStack(Items.STONE, 32), registries()).orElseThrow();

        assertEquals(one.stack().item(), many.stack().item());
        assertEquals(1, one.stack().count());
        assertEquals(32, many.stack().count());
    }

    @Test
    void persistentDamageChangesVariant() {
        ItemStack pristine = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack damaged = pristine.copy();
        damaged.setDamageValue(1);

        var pristineKey = mapper.map(pristine, registries()).orElseThrow().stack().item();
        var damagedKey = mapper.map(damaged, registries()).orElseThrow().stack().item();

        assertNotEquals(pristineKey, damagedKey);
        assertTrue(damagedKey.variant().startsWith("components:v1:"));
    }

    @Test
    void differentCustomDataChangesVariant() {
        ItemStack first = stackWithCustomValue(1);
        ItemStack second = stackWithCustomValue(2);

        var firstKey = mapper.map(first, registries()).orElseThrow().stack().item();
        var secondKey = mapper.map(second, registries()).orElseThrow().stack().item();

        assertNotEquals(firstKey, secondKey);
    }

    private static ItemStack stackWithCustomValue(int value) {
        ItemStack stack = new ItemStack(Items.STONE);
        CompoundTag data = new CompoundTag();
        data.putInt("value", value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    private static RegistryAccess.Frozen registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}
