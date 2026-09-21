package dev.litemfinder.neoforge.mapping;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentVariantFingerprintTest {

    private final ComponentVariantFingerprint fingerprints = new ComponentVariantFingerprint();

    @Test
    void emptyPersistentPatchUsesDefaultVariant() {
        assertEquals("", fingerprints.fingerprintEncodedPatch(JsonParser.parseString("{}")));
    }

    @Test
    void objectKeyOrderDoesNotChangeFingerprint() {
        String first = fingerprints.fingerprintEncodedPatch(
                JsonParser.parseString("{\"minecraft:damage\":7,\"minecraft:custom_data\":{\"b\":2,\"a\":1}}")
        );
        String second = fingerprints.fingerprintEncodedPatch(
                JsonParser.parseString("{\"minecraft:custom_data\":{\"a\":1,\"b\":2},\"minecraft:damage\":7}")
        );

        assertEquals(first, second);
        assertTrue(first.matches("components:v1:[0-9a-f]{64}"));
    }

    @Test
    void arrayOrderAndComponentValuesRemainSignificant() {
        String first = fingerprints.fingerprintEncodedPatch(JsonParser.parseString("{\"values\":[1,2]}"));
        String reordered = fingerprints.fingerprintEncodedPatch(JsonParser.parseString("{\"values\":[2,1]}"));
        String changed = fingerprints.fingerprintEncodedPatch(JsonParser.parseString("{\"values\":[1,3]}"));

        assertNotEquals(first, reordered);
        assertNotEquals(first, changed);
    }
}
