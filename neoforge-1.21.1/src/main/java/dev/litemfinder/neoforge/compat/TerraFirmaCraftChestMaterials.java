package dev.litemfinder.neoforge.compat;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.Optional;

/** Uses TFC's public texture accessor without making it a required dependency. */
public final class TerraFirmaCraftChestMaterials {

    private static final String CHEST = "net.dries007.tfc.common.blocks.wood.TFCChestBlock";
    private static final String TRAPPED = "net.dries007.tfc.common.blocks.wood.TFCTrappedChestBlock";

    private TerraFirmaCraftChestMaterials() {
    }

    public static Optional<Material> resolve(BlockState state, ChestType type) {
        Class<?> blockClass = state.getBlock().getClass();
        boolean tfc = false;
        boolean trapped = false;
        for (Class<?> current = blockClass; current != null; current = current.getSuperclass()) {
            tfc |= current.getName().equals(CHEST);
            trapped |= current.getName().equals(TRAPPED);
        }
        if (!tfc) {
            return Optional.empty();
        }
        try {
            Object texture = blockClass.getMethod("getTextureLocation").invoke(state.getBlock());
            if (texture instanceof String wood) {
                return Optional.of(new Material(Sheets.CHEST_SHEET, texture(wood, trapped, type)));
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            // Other TFC versions can retain the standard chest material and complete footprint.
        }
        return Optional.empty();
    }

    static ResourceLocation texture(String wood, boolean trapped, ChestType type) {
        String folder = trapped ? "trapped" : "normal";
        if (type != ChestType.SINGLE) {
            folder += type == ChestType.LEFT ? "_left" : "_right";
        }
        return ResourceLocation.fromNamespaceAndPath("tfc", "entity/chest/" + folder + "/" + wood);
    }
}
