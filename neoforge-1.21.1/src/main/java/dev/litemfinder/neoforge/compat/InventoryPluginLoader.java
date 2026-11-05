package dev.litemfinder.neoforge.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.litemfinder.neoforge.api.inventory.InventoryPlugin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Optional entrypoints are discovered from mod files, not resources supplied by a server or resource pack. */
public final class InventoryPluginLoader implements AutoCloseable {
    public static final String MANIFEST = "litemfinder.plugins.json";
    private final List<InventoryPluginContext> active = new ArrayList<>();
    private Report report = new Report(List.of());

    public record Manifest(String source, String json) { }
    public enum Status { LOADED, SKIPPED, FAILED }
    public record Outcome(String id, String source, Status status, String detail) { }
    public record Report(List<Outcome> outcomes) {
        public Report { outcomes = List.copyOf(outcomes); }
        public long count(Status status) { return outcomes.stream().filter(value -> value.status() == status).count(); }
    }

    public Report report() { return report; }

    public void loadNeoForge() {
        List<Manifest> manifests = new ArrayList<>();
        List<Outcome> errors = new ArrayList<>();
        for (var file : ModList.get().getModFiles()) {
            var path = file.getFile().findResource(MANIFEST);
            if (!Files.isRegularFile(path)) continue;
            String source = file.getMods().stream().map(mod -> mod.getModId()).findFirst().orElse("unknown-mod-file");
            try { manifests.add(new Manifest(source, Files.readString(path))); }
            catch (Exception failure) { errors.add(failed(source, source, failure)); }
        }
        Map<String, ArtifactVersion> mods = new TreeMap<>();
        ModList.get().getMods().forEach(mod -> mods.put(mod.getModId(), mod.getVersion()));
        load(manifests, mods, name -> {
            try {
                return Class.forName(name, true, InventoryPlugin.class.getClassLoader())
                        .asSubclass(InventoryPlugin.class).getConstructor().newInstance();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("cannot initialize inventory plugin", failure);
            }
        });
        if (!errors.isEmpty()) {
            List<Outcome> combined = new ArrayList<>(report.outcomes());
            combined.addAll(errors);
            report = new Report(combined);
        }
        LogUtils.getLogger().info("Inventory plugins: {} loaded, {} skipped, {} failed",
                report.count(Status.LOADED), report.count(Status.SKIPPED), report.count(Status.FAILED));
    }

    /** Injectable discovery boundary for dependency gating, failure isolation and rollback tests. */
    public Report load(List<Manifest> manifests, Map<String, ? extends ArtifactVersion> mods,
            Function<String, InventoryPlugin> instantiate) {
        close();
        List<Outcome> outcomes = new ArrayList<>();
        Map<String, List<Definition>> definitions = new TreeMap<>();
        for (Manifest manifest : manifests) {
            try {
                JsonObject root = JsonParser.parseString(manifest.json()).getAsJsonObject();
                for (var entry : root.entrySet()) {
                    try {
                        ResourceLocation id = ResourceLocation.parse(entry.getKey());
                        JsonObject value = entry.getValue().getAsJsonObject();
                        String entrypoint = value.get("entrypoint").getAsString();
                        if (entrypoint.isBlank()) throw new IllegalArgumentException("blank entrypoint");
                        Map<String, VersionRange> requirements = new TreeMap<>();
                        if (value.has("required")) {
                            for (var requirement : value.getAsJsonObject("required").entrySet()) {
                                String spec = requirement.getValue().getAsString();
                                // A bare Maven version is a recommendation, not an exact range.
                                if (!spec.startsWith("[") && !spec.startsWith("(")) {
                                    throw new IllegalArgumentException("use an explicit Maven version range");
                                }
                                requirements.put(requirement.getKey(), VersionRange.createFromVersionSpec(spec));
                            }
                        }
                        definitions.computeIfAbsent(id.toString(), ignored -> new ArrayList<>())
                                .add(new Definition(id.toString(), manifest.source(), entrypoint, requirements));
                    } catch (Exception | LinkageError failure) {
                        outcomes.add(failed(entry.getKey(), manifest.source(), failure));
                    }
                }
            } catch (RuntimeException | LinkageError failure) {
                outcomes.add(failed(manifest.source(), manifest.source(), failure));
            }
        }
        for (List<Definition> group : definitions.values()) {
            if (group.size() != 1) {
                group.forEach(value -> outcomes.add(new Outcome(value.id(), value.source(), Status.FAILED, "duplicate plugin id")));
                continue;
            }
            Definition definition = group.getFirst();
            String missing = definition.required().entrySet().stream().filter(entry ->
                    !mods.containsKey(entry.getKey()) || !entry.getValue().containsVersion(mods.get(entry.getKey())))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (missing != null) {
                outcomes.add(new Outcome(definition.id(), definition.source(), Status.SKIPPED,
                        "mod missing or version outside range: " + missing));
                continue;
            }
            InventoryPluginContext context = new InventoryPluginContext();
            try {
                InventoryPlugin plugin = java.util.Objects.requireNonNull(instantiate.apply(definition.entrypoint()));
                plugin.register(context);
                context.seal();
                active.add(context);
                outcomes.add(new Outcome(definition.id(), definition.source(), Status.LOADED, ""));
            } catch (RuntimeException | LinkageError failure) {
                try { context.close(); } catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                outcomes.add(failed(definition.id(), definition.source(), failure));
            }
        }
        report = new Report(outcomes);
        return report;
    }

    private static Outcome failed(String id, String source, Throwable failure) {
        LogUtils.getLogger().warn("Inventory plugin {} from {} failed ({})", id, source, failure.getClass().getName());
        return new Outcome(id, source, Status.FAILED, failure.getClass().getSimpleName());
    }

    @Override public void close() {
        for (int index = active.size() - 1; index >= 0; index--) active.get(index).close();
        active.clear();
    }

    private record Definition(String id, String source, String entrypoint, Map<String, VersionRange> required) { }
}
