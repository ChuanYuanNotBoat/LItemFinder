package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.neoforge.client.view.QuantityFormat;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only persisted display modes. Quantity values are always stored as integers. */
public final class ClientDisplayConfig {

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<QuantityFormat.Mode> OVERVIEW_MODE;
    private static final ModConfigSpec.EnumValue<QuantityFormat.Mode> EDITOR_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("quantity_display");
        OVERVIEW_MODE = builder.comment("Quantity style outside the editor")
                .defineEnum("overview", QuantityFormat.Mode.NUMBER);
        EDITOR_MODE = builder.comment("Quantity style while editing a retrieval request")
                .defineEnum("editor", QuantityFormat.Mode.BOX_STACK_ITEM);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientDisplayConfig() {
    }

    public static QuantityFormat.Mode overviewMode() {
        return OVERVIEW_MODE.get();
    }

    public static QuantityFormat.Mode editorMode() {
        return EDITOR_MODE.get();
    }

    public static void setOverviewMode(QuantityFormat.Mode mode) {
        OVERVIEW_MODE.set(mode);
    }

    public static void setEditorMode(QuantityFormat.Mode mode) {
        EDITOR_MODE.set(mode);
    }
}
