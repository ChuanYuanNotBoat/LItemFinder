package dev.litemfinder.navigation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Proposes vehicle/material preparation tasks from explicit stock and recipe evidence. */
public final class PreparationPlanner {

    private final Map<String, Recipe> recipes;
    private final int maxDepth;
    private final int maxTasks;

    public PreparationPlanner(Map<String, Recipe> recipes, int maxDepth, int maxTasks) {
        this.recipes = Map.copyOf(Objects.requireNonNull(recipes, "recipes must not be null"));
        if (this.recipes.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().output()))) {
            throw new IllegalArgumentException("recipe map keys must match output items");
        }
        if (maxDepth <= 0 || maxTasks <= 0) {
            throw new IllegalArgumentException("planning budgets must be positive");
        }
        this.maxDepth = maxDepth;
        this.maxTasks = maxTasks;
    }

    /** Preparation is allowed by default, but every generated task remains an unconfirmed proposal. */
    public Proposal propose(List<Requirement> requirements, List<Stock> stock) {
        return propose(requirements, stock, true);
    }

    public Proposal propose(List<Requirement> requirements, List<Stock> stock, boolean allowCrafting) {
        Objects.requireNonNull(requirements, "requirements must not be null");
        Objects.requireNonNull(stock, "stock must not be null");
        State state = new State(stock);
        for (Requirement requirement : requirements) {
            Objects.requireNonNull(requirement, "requirements must not contain null");
            try {
                state.fulfill(requirement.item(), requirement.count(), new HashSet<>(), 0, allowCrafting);
            } catch (PlanningFailure failure) {
                return new Proposal(failure.status, List.copyOf(state.tasks), failure.item, true);
            }
        }
        return new Proposal(Status.PROPOSED, List.copyOf(state.tasks), "", true);
    }

    public enum Status {
        PROPOSED,
        MISSING,
        CYCLE,
        BUDGET_EXCEEDED
    }

    public enum StockKind {
        OWNED,
        STORED
    }

    public enum TaskKind {
        USE_OWNED,
        RETRIEVE,
        CRAFT
    }

    public record Requirement(String item, long count) {

        public Requirement {
            item = nonBlank(item, "item");
            if (count <= 0) {
                throw new IllegalArgumentException("requirement count must be positive");
            }
        }
    }

    public record Stock(String sourceId, String item, long count, StockKind kind) {

        public Stock {
            sourceId = nonBlank(sourceId, "sourceId");
            item = nonBlank(item, "item");
            Objects.requireNonNull(kind, "kind must not be null");
            if (count <= 0) {
                throw new IllegalArgumentException("stock count must be positive");
            }
        }
    }

    public record Recipe(String output, long outputCount, Map<String, Long> ingredients) {

        public Recipe {
            output = nonBlank(output, "output");
            if (outputCount <= 0) {
                throw new IllegalArgumentException("recipe output count must be positive");
            }
            ingredients = Map.copyOf(Objects.requireNonNull(ingredients, "ingredients must not be null"));
            if (ingredients.isEmpty() || ingredients.entrySet().stream().anyMatch(entry ->
                    entry.getKey() == null || entry.getKey().isBlank()
                            || entry.getValue() == null || entry.getValue() <= 0)) {
                throw new IllegalArgumentException("recipes require positive ingredients");
            }
        }
    }

    /** Tasks are topologically ordered; dependency IDs refer only to earlier tasks. */
    public record Task(int id, TaskKind kind, String item, long count, String sourceId,
                       List<Integer> dependsOn) {

        public Task {
            if (id < 0 || count <= 0) {
                throw new IllegalArgumentException("task ID/count must be non-negative/positive");
            }
            Objects.requireNonNull(kind, "kind must not be null");
            item = nonBlank(item, "item");
            Objects.requireNonNull(sourceId, "sourceId must not be null");
            dependsOn = List.copyOf(Objects.requireNonNull(dependsOn, "dependsOn must not be null"));
            if (dependsOn.stream().anyMatch(dependency -> dependency == null || dependency < 0
                    || dependency >= id)) {
                throw new IllegalArgumentException("task dependencies must precede the task");
            }
        }
    }

    /** Requires explicit player confirmation before a caller may act on any task. */
    public record Proposal(Status status, List<Task> tasks, String blockingItem,
                           boolean requiresConfirmation) {

        public Proposal {
            Objects.requireNonNull(status, "status must not be null");
            tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks must not be null"));
            Objects.requireNonNull(blockingItem, "blockingItem must not be null");
        }

        public boolean actionable() {
            return status == Status.PROPOSED && !tasks.isEmpty();
        }
    }

    private final class State {

        private final List<Stock> sources;
        private final Map<String, Long> usedBySource = new HashMap<>();
        private final List<Task> tasks = new ArrayList<>();

        private State(List<Stock> sources) {
            this.sources = List.copyOf(sources);
            if (this.sources.stream().map(Stock::sourceId).distinct().count() != this.sources.size()) {
                throw new IllegalArgumentException("stock source IDs must be unique");
            }
        }

        private List<Integer> fulfill(String item, long count, Set<String> stack,
                                      int depth, boolean allowCrafting) {
            if (depth > maxDepth || tasks.size() >= maxTasks) {
                throw new PlanningFailure(Status.BUDGET_EXCEEDED, item);
            }
            long remaining = count;
            List<Integer> dependencies = new ArrayList<>();
            for (Stock source : sources) {
                if (!source.item().equals(item) || remaining == 0) {
                    continue;
                }
                long available = source.count() - usedBySource.getOrDefault(source.sourceId(), 0L);
                long reserved = Math.min(Math.max(0, available), remaining);
                if (reserved > 0) {
                    usedBySource.merge(source.sourceId(), reserved, Math::addExact);
                    dependencies.add(add(source.kind() == StockKind.OWNED ? TaskKind.USE_OWNED
                            : TaskKind.RETRIEVE, item, reserved, source.sourceId(), List.of()));
                    remaining -= reserved;
                }
            }
            if (remaining == 0) {
                return dependencies;
            }
            Recipe recipe = allowCrafting ? recipes.get(item) : null;
            if (recipe == null) {
                throw new PlanningFailure(Status.MISSING, item);
            }
            if (!recipe.output().equals(item) || !stack.add(item)) {
                throw new PlanningFailure(Status.CYCLE, item);
            }
            long batches = Math.floorDiv(remaining - 1, recipe.outputCount()) + 1;
            try {
                for (Map.Entry<String, Long> ingredient : new java.util.TreeMap<>(recipe.ingredients())
                        .entrySet()) {
                    long required = Math.multiplyExact(batches, ingredient.getValue());
                    dependencies.addAll(fulfill(ingredient.getKey(), required, stack,
                            depth + 1, allowCrafting));
                }
            } catch (ArithmeticException overflow) {
                throw new PlanningFailure(Status.BUDGET_EXCEEDED, item);
            } finally {
                stack.remove(item);
            }
            long output;
            try {
                output = Math.multiplyExact(batches, recipe.outputCount());
            } catch (ArithmeticException overflow) {
                throw new PlanningFailure(Status.BUDGET_EXCEEDED, item);
            }
            add(TaskKind.CRAFT, item, output, "", dependencies);
            return List.of(tasks.getLast().id());
        }

        private int add(TaskKind kind, String item, long count, String source, List<Integer> dependencies) {
            if (tasks.size() >= maxTasks) {
                throw new PlanningFailure(Status.BUDGET_EXCEEDED, item);
            }
            int id = tasks.size();
            tasks.add(new Task(id, kind, item, count, source, dependencies));
            return id;
        }
    }

    private static final class PlanningFailure extends RuntimeException {

        private final Status status;
        private final String item;

        private PlanningFailure(Status status, String item) {
            this.status = status;
            this.item = item;
        }
    }

    private static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
