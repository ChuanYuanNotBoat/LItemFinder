package dev.litemfinder.core.optimizer;

import dev.litemfinder.core.classification.AnyOfRule;
import dev.litemfinder.core.classification.ExactItemRule;
import dev.litemfinder.core.classification.ItemTagResolver;
import dev.litemfinder.core.classification.StorageClassifier;
import dev.litemfinder.core.classification.StorageGroup;
import dev.litemfinder.core.classification.StorageGroupId;
import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.SlotSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageOptimizerTest {

    @Test
    void plansMisclassifiedLeafItemsAndAvoidsMovingNestedContainerItself() {
        ItemKey iron = ItemKey.parse("minecraft:iron_ingot");
        ItemKey diamond = ItemKey.parse("minecraft:diamond");
        ItemKey stone = ItemKey.parse("minecraft:stone");
        ItemKey apple = ItemKey.parse("minecraft:apple");
        InventorySnapshot shulker = new InventorySnapshot(
                container("shulker-a", ContainerType.SHULKER_BOX),
                27,
                Instant.EPOCH,
                List.of(new SlotSnapshot(12, stack(diamond, 5)))
        );
        InventorySnapshot source = new InventorySnapshot(
                container("mixed-chest", ContainerType.CHEST),
                27,
                Instant.EPOCH,
                List.of(
                        new SlotSnapshot(0, stack(iron, 32)),
                        new SlotSnapshot(1, stack(stone, 64)),
                        SlotSnapshot.nested(2, stack(ItemKey.parse("minecraft:blue_shulker_box"), 1), shulker),
                        new SlotSnapshot(3, stack(apple, 4))
                )
        );
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        index.update(source);

        StorageGroup minerals = new StorageGroup(
                new StorageGroupId("minerals"),
                "Minerals",
                List.of(new ContainerId("mineral-chest")),
                new AnyOfRule(List.of(new ExactItemRule(iron), new ExactItemRule(diamond))),
                20
        );
        StorageGroup building = new StorageGroup(
                new StorageGroupId("building"),
                "Building",
                List.of(new ContainerId("mixed-chest")),
                new ExactItemRule(stone),
                10
        );
        StorageOptimizer optimizer = new StorageOptimizer(
                index,
                new StorageClassifier(List.of(minerals, building), ItemTagResolver.empty())
        );

        OptimizationPlan plan = optimizer.optimize();

        assertEquals(2, plan.tasks().size());
        assertEquals(37, plan.totalMoveAmount());
        assertEquals(List.of(iron, diamond), plan.tasks().stream().map(MoveTask::item).toList());
        assertEquals("shulker-a", plan.tasks().get(1).source().path().leaf().value());
        assertEquals(1, plan.alreadyPlaced().size());
        assertEquals(apple, plan.unclassified().getFirst().stack().item());
        assertEquals(ItemKey.parse("minecraft:blue_shulker_box"),
                plan.nestedContainerItems().getFirst().stack().item());
    }

    private static ContainerRecord container(String id, ContainerType type) {
        return new ContainerRecord(
                new ContainerId(id),
                type,
                new LogicalLocation("test", "container/" + id)
        );
    }

    private static ItemStackInfo stack(ItemKey item, long count) {
        return new ItemStackInfo(item, count);
    }
}
