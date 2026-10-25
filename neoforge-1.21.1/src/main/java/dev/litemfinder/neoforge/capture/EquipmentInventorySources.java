package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.EquipmentInventorySource;
import dev.litemfinder.neoforge.compat.CuriosInventoryReader;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Extensible equipment capture. A temporarily unavailable source retains its last observed slots. */
public final class EquipmentInventorySources implements EquipmentInventorySource {
    private static final CopyOnWriteArrayList<EquipmentInventorySource> SOURCES =
            new CopyOnWriteArrayList<>(List.of(new CuriosInventoryReader()));
    private final Map<String, List<AccessorySlot>> latest = new TreeMap<>();
    private LivingEntity wearer;

    public static synchronized AutoCloseable register(EquipmentInventorySource source) {
        if (source.id().isBlank() || SOURCES.stream().anyMatch(value -> value.id().equals(source.id()))) {
            throw new IllegalArgumentException("equipment source must have a unique nonempty id");
        }
        SOURCES.add(source);
        return () -> SOURCES.remove(source);
    }

    @Override
    public String id() {
        return "equipment";
    }

    @Override
    public Optional<List<AccessorySlot>> read(LivingEntity player) {
        if (wearer != player) {
            latest.clear();
            wearer = player;
        }
        latest.keySet().removeIf(id -> SOURCES.stream().noneMatch(source -> source.id().equals(id)));
        for (var source : SOURCES) {
            try {
                source.read(player).ifPresent(value -> latest.put(source.id(), List.copyOf(value)));
            } catch (RuntimeException | LinkageError ignored) {
                // Preserve the last observation until the optional source becomes readable again.
            }
        }
        List<AccessorySlot> result = new ArrayList<>();
        latest.forEach((source, slots) -> slots.forEach(slot -> result.add(
                new AccessorySlot(source + "/" + slot.identifier(), slot.index(), slot.stack()))));
        result.sort(java.util.Comparator.comparing(AccessorySlot::identifier).thenComparingInt(AccessorySlot::index));
        return Optional.of(List.copyOf(result));
    }

    public void clear() {
        latest.clear();
        wearer = null;
    }
}
