package dev.litemfinder.core.classification;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageClassifierTest {

    @Test
    void resolvesTagNamespaceAndExactRulesByPriority() {
        ItemKey iron = ItemKey.parse("minecraft:iron_ingot");
        NamespacedId ingots = NamespacedId.parse("c:ingots");
        ItemTagResolver tags = item -> Map.of(iron, Set.of(ingots)).getOrDefault(item, Set.of());

        StorageGroup minecraft = group(
                "minecraft",
                new NamespaceRule("minecraft"),
                10,
                "general-chest"
        );
        StorageGroup metals = group("metals", new TagRule(ingots), 20, "metal-chest");
        StorageGroup priorityIron = group("iron", new ExactItemRule(iron), 30, "iron-chest");
        StorageClassifier classifier = new StorageClassifier(
                List.of(minecraft, metals, priorityIron),
                tags
        );

        assertEquals(priorityIron, classifier.classify(iron).orElseThrow());
        assertEquals(minecraft, classifier.classify(ItemKey.parse("minecraft:stone")).orElseThrow());
        assertTrue(classifier.classify(ItemKey.parse("create:cogwheel")).isEmpty());
    }

    @Test
    void breaksEqualPriorityTiesByStableGroupId() {
        ItemRule rule = new NamespaceRule("minecraft");
        StorageGroup second = group("z-group", rule, 10, "z-chest");
        StorageGroup first = group("a-group", rule, 10, "a-chest");

        StorageClassifier classifier = new StorageClassifier(
                List.of(second, first),
                ItemTagResolver.empty()
        );

        assertEquals(first, classifier.classify(ItemKey.parse("minecraft:dirt")).orElseThrow());
    }

    private static StorageGroup group(String id, ItemRule rule, int priority, String containerId) {
        return new StorageGroup(
                new StorageGroupId(id),
                id,
                List.of(new ContainerId(containerId)),
                rule,
                priority
        );
    }
}
