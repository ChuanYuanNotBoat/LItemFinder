package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.model.WorldPosition;
import dev.litemfinder.core.view.NavigationReport;
import dev.litemfinder.core.view.NavigationSummary;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** All user-facing route wording is localized here; geometry and coverage rules remain in Core. */
final class NavigationSummaryText {
    private static final String PREFIX = "gui.litemfinder.navigation.";
    private static final Set<String> MODES = Set.of("WALK", "ICE_BOAT", "WATER_BOAT", "RAIL", "PORTAL", "ELYTRA", "MODDED");
    private NavigationSummaryText() { }

    static List<Component> summary(NavigationSummary value, Component destination) {
        if (value.stops().isEmpty()) return List.of(text("idle"));
        List<Component> lines = new ArrayList<>();
        lines.add(text("destination", destination, value.stops().size()));
        Component modes = value.modes().isEmpty() ? text(value.state() == NavigationSummary.State.FOUND ? "mode.none" : "mode.unknown")
                : join(value.modes().stream().map(NavigationSummaryText::mode).toList());
        Component duration = value.knownSeconds().isEmpty() ? text("time.unknown")
                : text(value.partialEstimate() ? "time.partial" : "time.total", duration(value.estimatedSeconds()));
        lines.add(join(List.of(text("state." + key(value.state())), modes, duration)));
        if (value.updating()) lines.add(text("updating"));
        if (value.verifiedStops().isPresent()) lines.add(text("coverage", value.verifiedStops().getAsInt(), value.stops().size()));
        if (value.state() == NavigationSummary.State.FOUND || value.state() == NavigationSummary.State.PARTIAL) {
            lines.add(text("estimate." + key(value.report().estimate())));
        }
        if (value.legs().isEmpty() && value.state() != NavigationSummary.State.FOUND) lines.add(text("schematic"));
        if (value.state() == NavigationSummary.State.CALCULATING) lines.add(text("calculating"));
        else if (value.state() != NavigationSummary.State.SCHEMATIC) {
            var reason = value.report().reason();
            boolean specified = reason != NavigationReport.Reason.NONE && reason != NavigationReport.Reason.UNSPECIFIED;
            if (specified) lines.add(text("reason." + key(reason)));
            lines.add(text(specified ? "action." + key(reason) : "action." + key(value.state())));
        }
        return List.copyOf(lines);
    }

    static List<Component> details(NavigationSummary value, Component destination) {
        List<Component> lines = new ArrayList<>(summary(value, destination));
        lines.add(text("snapshot"));
        lines.add(text("evidence.source", text("source." + key(value.report().source()))));
        lines.add(text("evidence.confidence", text("confidence." + key(value.report().confidence()))));
        lines.add(text("evidence.time", value.report().checkedAt().<Component>map(time ->
                Component.literal(SnapshotTimeFormatter.format(time))).orElseGet(() -> text("unspecified"))));
        value.gapStart().ifPresent(point -> lines.add(text("gap", position(point))));
        lines.add(text("stops"));
        for (int index = 0; index < value.stops().size(); index++) {
            lines.add(text("stop", index + 1, position(WorldPosition.at(value.stops().get(index)))));
        }
        lines.add(text("legs"));
        for (int index = 0; index < value.legs().size(); index++) {
            var leg = value.legs().get(index);
            if (index > 0) {
                var previous = value.legs().get(index - 1);
                if (!previous.mode().equals(leg.mode())) lines.add(text("transfer", mode(previous.mode()), mode(leg.mode())));
                if (!previous.end().dimension().equals(leg.start().dimension())) {
                    lines.add(text("dimension_change", previous.end().dimension().toString(), leg.start().dimension().toString()));
                }
            }
            lines.add(text("leg", index + 1, mode(leg.mode()), leg.estimatedSeconds().isPresent()
                    ? text("time.leg", duration(leg.estimatedSeconds().getAsDouble())) : text("time.leg_unknown")));
            lines.add(text("from_to", position(leg.start()), position(leg.end())));
            if (leg.changesDimension()) lines.add(text("dimension_change", leg.start().dimension().toString(), leg.end().dimension().toString()));
        }
        if (value.legs().isEmpty()) lines.add(text("legs.empty"));
        return List.copyOf(lines);
    }

    static Component mode(String value) { return text("mode." + (MODES.contains(value) ? value.toLowerCase(Locale.ROOT) : "modded")); }
    private static String key(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private static Component duration(double seconds) {
        return seconds < 60 ? text("duration.seconds", (long) Math.ceil(seconds))
                : text("duration.minutes", (long) Math.ceil(seconds / 60));
    }
    private static Component position(WorldPosition point) {
        return text("position", point.dimension().toString(), String.format(Locale.ROOT, "%.2f, %.2f, %.2f", point.x(), point.y(), point.z()));
    }
    private static Component text(String key, Object... values) { return Component.translatable(PREFIX + key, values); }
    private static Component join(List<Component> values) {
        var joined = Component.empty();
        for (Component value : values) { if (!joined.getString().isEmpty()) joined.append(" · "); joined.append(value); }
        return joined;
    }
}
