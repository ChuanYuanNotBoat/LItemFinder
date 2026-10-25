package dev.litemfinder.neoforge.compat;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerraFirmaCraftChestMaterialsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void usesTfcWoodAndSeparateNormalAndTrappedHalfTextures() {
        assertEquals("tfc:entity/chest/normal/oak",
                TerraFirmaCraftChestMaterials.texture("oak", false, ChestType.SINGLE).toString());
        assertEquals("tfc:entity/chest/normal_left/ash",
                TerraFirmaCraftChestMaterials.texture("ash", false, ChestType.LEFT).toString());
        assertEquals("tfc:entity/chest/normal_right/ash",
                TerraFirmaCraftChestMaterials.texture("ash", false, ChestType.RIGHT).toString());
        assertEquals("tfc:entity/chest/trapped_left/maple",
                TerraFirmaCraftChestMaterials.texture("maple", true, ChestType.LEFT).toString());
        assertEquals("tfc:entity/chest/trapped_right/maple",
                TerraFirmaCraftChestMaterials.texture("maple", true, ChestType.RIGHT).toString());
    }

    @Test
    void vanillaChestNeedsNoTfcDependency() {
        assertTrue(TerraFirmaCraftChestMaterials.resolve(Blocks.CHEST.defaultBlockState(), ChestType.SINGLE).isEmpty());
    }
}
