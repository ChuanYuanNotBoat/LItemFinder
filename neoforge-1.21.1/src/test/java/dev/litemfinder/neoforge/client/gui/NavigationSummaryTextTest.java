package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import dev.litemfinder.core.view.NavigationReport;
import dev.litemfinder.core.view.NavigationSummary;
import dev.litemfinder.neoforge.client.navigation.NavigationGateway;
import dev.litemfinder.neoforge.client.navigation.NavigationSummaries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class NavigationSummaryTextTest {
    private static final List<WorldLocation> STOPS = List.of(new WorldLocation("world", "minecraft:overworld", 30, 64, 0));
    private static final List<WorldPosition> POINTS = List.of(new WorldPosition("world", "minecraft:overworld", 0, 64, 0),
            new WorldPosition("world", "minecraft:overworld", 10, 64, 0));
    private static NavigationSummary partial(NavigationReport.Reason reason) {
        return new NavigationSummary(NavigationSummary.State.PARTIAL, false, STOPS, List.of(
                new NavigationSummary.Leg("WALK", POINTS, OptionalDouble.of(12))), 12,
                new NavigationReport(reason, NavigationReport.Estimate.BASE_SPEED, 0, Optional.empty(),
                        NavigationReport.Confidence.UNSPECIFIED, NavigationReport.Source.UNSPECIFIED));
    }
    private static String joined(List<Component> lines) { return String.join("\n", lines.stream().map(Component::getString).toList()); }

    @Test void bothLanguagesExplainPartialTimeTheGapAndNextActionWithoutInternalEnumNames() throws Exception {
        for (String locale : List.of("en_us", "zh_cn")) try (var ignored = new Translation(locale)) {
            String summary = joined(NavigationSummaryText.summary(partial(NavigationReport.Reason.BUDGET), Component.literal("Chest")));
            assertTrue(summary.contains(locale.equals("en_us") ? "Verified part: about 12 s" : "仅已验证部分约 12 秒"));
            assertTrue(summary.contains(locale.equals("en_us") ? "Move closer" : "靠近目标"));
            assertTrue(summary.contains("0/1"));
            assertFalse(summary.contains("PARTIAL") || summary.contains("WALK") || summary.contains("gui.litemfinder"));
            String details = joined(NavigationSummaryText.details(partial(NavigationReport.Reason.BUDGET), Component.literal("Chest")));
            assertTrue(details.contains("10.00, 64.00, 0.00"));
            assertTrue(details.contains(locale.equals("en_us") ? "no confidence assumed" : "不推定置信度"));
        }
    }
    @Test void unknownAndUnreachableResultsRemainReadableWithoutAZeroSecondArrival() throws Exception {
        try (var ignored = new Translation("en_us")) {
            for (var state : List.of(NavigationSummary.State.UNKNOWN, NavigationSummary.State.UNREACHABLE, NavigationSummary.State.FAILED)) {
                var value = new NavigationSummary(state, false, STOPS, List.of(), 0, NavigationReport.unspecified());
                String result = joined(NavigationSummaryText.summary(value, Component.literal("Chest")));
                assertTrue(result.contains("Full time unknown"));
                assertFalse(result.contains("0 s") || result.contains("gui.litemfinder"));
            }
        }
    }
    @Test void unfamiliarProviderModesHaveANaturalFallbackAndKeepDetailsSeparatedByTransfer() throws Exception {
        try (var ignored = new Translation("en_us")) {
            var other = new NavigationSummary.Leg("example:transit", List.of(POINTS.getLast(),
                    new WorldPosition("world", "minecraft:the_nether", 1, 64, 0)), OptionalDouble.empty());
            var value = new NavigationSummary(NavigationSummary.State.PARTIAL, false, STOPS,
                    List.of(partial(NavigationReport.Reason.BUDGET).legs().getFirst(), other), 12, NavigationReport.unspecified());
            String result = joined(NavigationSummaryText.details(value, Component.literal("Chest")));
            assertTrue(result.contains("Other transport") && result.contains("Transfer:") && result.contains("Dimension change:"));
            assertTrue(result.contains("Time not provided"));
            assertFalse(result.contains("example:transit") || result.contains("gui.litemfinder"));
        }
    }
    @Test void everyReasonAndStateHasBothTranslations() throws Exception {
        for (String locale : List.of("en_us", "zh_cn")) try (var ignored = new Translation(locale)) {
            for (var reason : NavigationReport.Reason.values()) {
                String result = joined(NavigationSummaryText.details(partial(reason), Component.literal("Chest")));
                assertFalse(result.contains("gui.litemfinder"), result);
            }
            for (var state : NavigationSummary.State.values()) {
                var value = new NavigationSummary(state, false, state == NavigationSummary.State.IDLE ? List.of() : STOPS,
                        List.of(), 0, NavigationReport.unspecified());
                assertFalse(joined(NavigationSummaryText.summary(value, Component.literal("Chest"))).contains("gui.litemfinder"), state.name());
            }
        }
    }
    @Test void legacyProvidersKeepTheirConstructorsAndDoNotAcquireFabricatedEvidence() {
        var segment = new NavigationGateway.Segment("WALK", POINTS);
        var overlay = new NavigationGateway.Overlay(NavigationGateway.Status.PARTIAL, List.of(segment), 12);
        var summary = NavigationSummaries.from(STOPS, overlay, true, true, false);
        assertTrue(segment.estimatedSeconds().isEmpty());
        assertEquals(NavigationReport.Source.UNSPECIFIED, summary.report().source());
        assertTrue(summary.updating());
        assertTrue(summary.verifiedStops().isEmpty());
    }

    private static final class Translation implements AutoCloseable {
        final Language previous = Language.getInstance();
        Translation(String locale) throws Exception {
            Map<String, String> translations = new HashMap<>();
            try (var input = getClass().getResourceAsStream("/assets/litemfinder/lang/" + locale + ".json")) {
                assertNotNull(input);
                Language.loadFromJson(input, translations::put);
            }
            Language.inject(new Language() {
                @Override public String getOrDefault(String key, String fallback) { return translations.getOrDefault(key, fallback); }
                @Override public boolean has(String key) { return translations.containsKey(key); }
                @Override public boolean isDefaultRightToLeft() { return false; }
                @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return FormattedCharSequence.forward(text.getString(), Style.EMPTY); }
            });
        }
        @Override public void close() { Language.inject(previous); }
    }
}
