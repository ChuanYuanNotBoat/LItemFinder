package dev.litemfinder.neoforge.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.search.SearchQuery;
import dev.litemfinder.core.search.SearchResult;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Development-only client commands for inspecting the read-only index. */
public final class ClientDebugCommands {

    private static final int DISPLAY_LIMIT = 10;

    private final ItemFinderClientApi client;
    private final Runnable openOverview;

    public ClientDebugCommands(ItemFinderClientApi client, Runnable openOverview) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.openOverview = Objects.requireNonNull(openOverview, "openOverview must not be null");
    }

    public void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("litemfinder")
                .executes(this::showHelp)
                .then(Commands.literal("gui").executes(this::openGui))
                .then(Commands.literal("stats").executes(this::showStats))
                .then(Commands.literal("search")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(this::search)))
                .then(Commands.literal("exact")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .executes(this::findExactItemId)))
                .then(Commands.literal("clear")
                        .executes(this::showClearConfirmation)
                        .then(Commands.literal("confirm").executes(this::clearCurrentScope))));
    }

    private int showHelp(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), "LItem Finder: /litemfinder gui | stats | search <text> | exact <namespace:item> | clear confirm");
        return 1;
    }

    private int openGui(CommandContext<CommandSourceStack> context) {
        openOverview.run();
        return 1;
    }

    private int showStats(CommandContext<CommandSourceStack> context) {
        var status = client.status();
        var capture = status.capture();
        String message = "LItem Finder index: roots=" + status.rootContainers()
                + ", entries=" + status.entries()
                + ", variants=" + status.variants()
                + ", tagCache=" + status.cachedTagItems()
                + "\nCapture diagnostics: emitted=" + capture.captured()
                + ", skipped=" + capture.skipped()
                + ", sessionOnly=" + capture.sessionOnly()
                + ", removed=" + capture.removed()
                + "\nSkipped reasons: " + formatCounters(capture.skippedReasons())
                + "\nSession-only reasons: " + formatCounters(capture.sessionOnlyReasons())
                + "\nRemoval reasons: " + formatCounters(capture.removalReasons());
        reply(context.getSource(), message);
        return 1;
    }

    private int search(CommandContext<CommandSourceStack> context) {
        String text = StringArgumentType.getString(context, "text");
        var response = client.search(SearchQuery.all().withText(text));
        if (response.isEmpty()) {
            reply(context.getSource(), "No indexed items match: " + text);
            return 0;
        }
        reply(context.getSource(), formatResults("Matches for " + text, response.results()));
        return response.results().size();
    }

    private int findExactItemId(CommandContext<CommandSourceStack> context) {
        String input = StringArgumentType.getString(context, "item").toLowerCase(Locale.ROOT);
        NamespacedId itemId;
        try {
            itemId = NamespacedId.parse(input);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Expected an item ID such as minecraft:diamond"));
            return 0;
        }

        var result = client.findByItemId(itemId);
        if (result.isEmpty()) {
            reply(context.getSource(), "No indexed stacks for " + itemId);
            return 0;
        }
        reply(context.getSource(), itemId + ": count=" + result.totalCount() + ", stacks=" + result.entries().size()
                + ", variants=" + result.variantCount() + ", roots=" + result.rootContainerCount());
        return result.entries().size();
    }

    private int showClearConfirmation(CommandContext<CommandSourceStack> context) {
        reply(context.getSource(), "This deletes the current world/server index. Run /litemfinder clear confirm to continue.");
        return 1;
    }

    private int clearCurrentScope(CommandContext<CommandSourceStack> context) {
        var result = client.clearCurrentScopeData();
        if (!result.successful()) {
            context.getSource().sendFailure(Component.literal("Could not clear the index; see the log for details."));
            return 0;
        }
        reply(context.getSource(), "Cleared current scope index (" + result.deletedRootSnapshots()
                + " persisted root snapshots).");
        return 1;
    }

    private static String formatResults(String heading, List<SearchResult> results) {
        StringBuilder message = new StringBuilder(heading)
                .append(": ")
                .append(results.size())
                .append(" item variants, total=")
                .append(results.stream().mapToLong(SearchResult::totalCount).sum());
        results.stream().limit(DISPLAY_LIMIT).forEach(result -> message
                .append("\n- ")
                .append(displayItem(result.item()))
                .append(" ×")
                .append(result.totalCount())
                .append(" in ")
                .append(result.entries().stream().map(entry -> entry.rootContainer().id()).distinct().count())
                .append(" roots"));
        if (results.size() > DISPLAY_LIMIT) {
            message.append("\n… and ").append(results.size() - DISPLAY_LIMIT).append(" more variants");
        }
        return message.toString();
    }

    private static String displayItem(ItemKey item) {
        return item.itemId() + (item.hasVariant() ? " (variant)" : "");
    }

    private static String formatCounters(java.util.Map<String, Long> counters) {
        if (counters.isEmpty()) {
            return "none";
        }
        return counters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static void reply(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
