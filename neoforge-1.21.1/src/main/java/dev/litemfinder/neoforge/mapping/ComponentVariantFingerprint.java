package dev.litemfinder.neoforge.mapping;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeMap;

/** Produces the opaque v1 variant from persistent Minecraft data components. */
public final class ComponentVariantFingerprint {

    private static final String PREFIX = "components:v1:";

    public String fingerprint(ItemStack stack, HolderLookup.Provider registries) {
        Objects.requireNonNull(stack, "stack must not be null");
        Objects.requireNonNull(registries, "registries must not be null");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("cannot fingerprint an empty ItemStack");
        }

        JsonElement encoded = DataComponentPatch.CODEC
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack.getComponentsPatch())
                .getOrThrow(message -> new IllegalArgumentException(
                        "failed to encode persistent item components: " + message
                ));
        return fingerprintEncodedPatch(encoded);
    }

    /** Visible for deterministic golden tests without bootstrapping a game registry. */
    public String fingerprintEncodedPatch(JsonElement encodedPatch) {
        Objects.requireNonNull(encodedPatch, "encodedPatch must not be null");
        if (encodedPatch.isJsonObject() && encodedPatch.getAsJsonObject().size() == 0) {
            return "";
        }

        byte[] canonical = canonicalJson(encodedPatch).getBytes(StandardCharsets.UTF_8);
        try {
            return PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    static String canonicalJson(JsonElement element) {
        StringBuilder output = new StringBuilder();
        appendCanonical(element, output);
        return output.toString();
    }

    private static void appendCanonical(JsonElement element, StringBuilder output) {
        if (element == null || element.isJsonNull()) {
            output.append("null");
        } else if (element.isJsonPrimitive()) {
            output.append(element);
        } else if (element.isJsonArray()) {
            output.append('[');
            boolean first = true;
            for (JsonElement child : element.getAsJsonArray()) {
                if (!first) {
                    output.append(',');
                }
                appendCanonical(child, output);
                first = false;
            }
            output.append(']');
        } else {
            output.append('{');
            boolean first = true;
            JsonObject object = element.getAsJsonObject();
            for (var entry : new TreeMap<>(object.asMap()).entrySet()) {
                if (!first) {
                    output.append(',');
                }
                output.append(new com.google.gson.JsonPrimitive(entry.getKey())).append(':');
                appendCanonical(entry.getValue(), output);
                first = false;
            }
            output.append('}');
        }
    }
}
