package dev.litemfinder.neoforge.client.gui;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotTimeFormatterTest {

    @Test
    void formatsInstantAsAReadableStringTranslationArgument() {
        String formatted = SnapshotTimeFormatter.format(
                Instant.parse("2026-09-23T11:27:25.095945500Z"),
                ZoneId.of("Asia/Singapore")
        );

        assertEquals("2026-09-23 19:27:25 +08:00", formatted);
        assertDoesNotThrow(() -> Component.translatable("gui.litemfinder.contents.stale", formatted));
    }
}
