package dev.litemfinder.neoforge.client.gui.map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.litemfinder.neoforge.compat.TerraFirmaCraftChestMaterials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Renders closed chest halves directly; the item renderer always substitutes a single chest. */
public final class ContainerSceneRenderer {

    private final Map<ChestType, ModelPart> chestModels = new EnumMap<>(ChestType.class);
    private final Map<BlockState, Material> chestMaterials = new HashMap<>();

    public void render(Minecraft minecraft, BlockState state, PoseStack pose, MultiBufferSource buffers) {
        if (!(state.getBlock() instanceof ChestBlock)) {
            minecraft.getBlockRenderer().renderSingleBlock(state, pose, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            return;
        }
        ChestType type = state.getValue(ChestBlock.TYPE);
        ModelPart model = chestModels.computeIfAbsent(type, key -> minecraft.getEntityModels().bakeLayer(switch (key) {
            case SINGLE -> ModelLayers.CHEST;
            case LEFT -> ModelLayers.DOUBLE_CHEST_LEFT;
            case RIGHT -> ModelLayers.DOUBLE_CHEST_RIGHT;
        }));
        Material material = chestMaterials.computeIfAbsent(state, ignored ->
                TerraFirmaCraftChestMaterials.resolve(state, type).orElseGet(() -> vanillaMaterial(state, type)));
        pose.pushPose();
        try {
            pose.translate(0.5F, 0.5F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(ChestBlock.FACING).toYRot()));
            pose.translate(-0.5F, -0.5F, -0.5F);
            model.render(pose, material.buffer(buffers, RenderType::entityCutout),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } finally {
            pose.popPose();
        }
    }

    public void clear() {
        chestModels.clear();
        chestMaterials.clear();
    }

    private static Material vanillaMaterial(BlockState state, ChestType type) {
        boolean trapped = state.is(Blocks.TRAPPED_CHEST) || state.getBlock() instanceof TrappedChestBlock;
        return switch (type) {
            case SINGLE -> trapped ? Sheets.CHEST_TRAP_LOCATION : Sheets.CHEST_LOCATION;
            case LEFT -> trapped ? Sheets.CHEST_TRAP_LOCATION_LEFT : Sheets.CHEST_LOCATION_LEFT;
            case RIGHT -> trapped ? Sheets.CHEST_TRAP_LOCATION_RIGHT : Sheets.CHEST_LOCATION_RIGHT;
        };
    }
}
