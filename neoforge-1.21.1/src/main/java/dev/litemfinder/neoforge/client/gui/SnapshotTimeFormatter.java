package dev.litemfinder.neoforge.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** Converts snapshot instants to safe, readable translation arguments. */
final class SnapshotTimeFormatter {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss XXX", Locale.ROOT);

    private SnapshotTimeFormatter() {
    }

    static String format(Instant observedAt) {
        return format(observedAt, ZoneId.systemDefault());
    }

    static String format(Instant observedAt, ZoneId zone) {
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        return FORMATTER.withZone(zone).format(observedAt);
    }
}
