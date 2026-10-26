package dev.litemfinder.neoforge.client.gui;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.litemfinder.core.view.FluidOverview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native fluid names/textures are decoded only in the client adapter. */
public final class FluidVisualResolver {
    private final Map<String, Visual> cache = new LinkedHashMap<>(16, 0.75f, true);
    public Visual resolve(FluidOverview.Row row) {
        String data = row.sources().getFirst().tank().encoded();
        Visual cached = cache.get(data);
        if (cached != null) return cached;
        Visual visual = new Visual(row.fluidId(), 0xFF477A96, null);
        try {
            var minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.level != null) {
                var fluid = FluidStack.CODEC.parse(minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(data)).getOrThrow();
                var extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
                var texture = extensions.getStillTexture(fluid);
                visual = new Visual(fluid.getFluid().getFluidType().getDescription(fluid).getString(), extensions.getTintColor(fluid) | 0xFF000000,
                        texture == null ? null : minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture));
            }
        } catch (RuntimeException | LinkageError unavailable) { }
        cache.put(data, visual);
        if (cache.size() > 256) cache.remove(cache.keySet().iterator().next());
        return visual;
    }
    public void clear() { cache.clear(); }
    public record Visual(String name, int color, TextureAtlasSprite sprite) {
        public void icon(GuiGraphics graphics, int x, int y) {
            if (sprite == null) graphics.fill(x, y, x + 16, y + 16, color);
            else graphics.blit(x, y, 0, 16, 16, sprite,
                    ((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1f);
        }
    }
}
