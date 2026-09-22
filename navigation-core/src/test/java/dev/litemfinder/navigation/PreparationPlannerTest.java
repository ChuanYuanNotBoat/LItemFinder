package dev.litemfinder.navigation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparationPlannerTest {

    @Test
    void recommendsRetrievalAndCraftingButRequiresConfirmation() {
        PreparationPlanner planner = new PreparationPlanner(Map.of(
                "minecraft:boat", new PreparationPlanner.Recipe("minecraft:boat", 1,
                        Map.of("minecraft:planks", 5L))
        ), 8, 32);

        var proposal = planner.propose(List.of(new PreparationPlanner.Requirement("minecraft:boat", 1)),
                List.of(new PreparationPlanner.Stock("chest-1/slot-3", "minecraft:planks", 5,
                        PreparationPlanner.StockKind.STORED)));

        assertEquals(PreparationPlanner.Status.PROPOSED, proposal.status());
        assertTrue(proposal.requiresConfirmation());
        assertEquals(PreparationPlanner.TaskKind.RETRIEVE, proposal.tasks().get(0).kind());
        assertEquals(PreparationPlanner.TaskKind.CRAFT, proposal.tasks().get(1).kind());
        assertEquals(List.of(0), proposal.tasks().get(1).dependsOn());
    }

    @Test
    void sharedSourceCannotSatisfyTwoRequirements() {
        PreparationPlanner planner = new PreparationPlanner(Map.of(), 8, 32);
        var proposal = planner.propose(List.of(
                new PreparationPlanner.Requirement("minecraft:iron_ingot", 4),
                new PreparationPlanner.Requirement("minecraft:iron_ingot", 4)
        ), List.of(new PreparationPlanner.Stock("chest/slot", "minecraft:iron_ingot", 6,
                PreparationPlanner.StockKind.STORED)));

        assertEquals(PreparationPlanner.Status.MISSING, proposal.status());
        assertFalse(proposal.actionable());
        assertEquals(6, proposal.tasks().stream().mapToLong(PreparationPlanner.Task::count).sum());
    }

    @Test
    void reportsRecipeCycleWithoutUnboundedExpansion() {
        PreparationPlanner planner = new PreparationPlanner(Map.of(
                "boat", new PreparationPlanner.Recipe("boat", 1, Map.of("plank", 1L)),
                "plank", new PreparationPlanner.Recipe("plank", 1, Map.of("boat", 1L))
        ), 8, 32);

        var proposal = planner.propose(List.of(new PreparationPlanner.Requirement("boat", 1)), List.of());

        assertEquals(PreparationPlanner.Status.CYCLE, proposal.status());
        assertFalse(proposal.actionable());
    }
}
