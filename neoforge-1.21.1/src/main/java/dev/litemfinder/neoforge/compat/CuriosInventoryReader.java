package dev.litemfinder.neoforge.compat;

import com.mojang.logging.LogUtils;
import dev.litemfinder.neoforge.capture.AccessorySlot;
import dev.litemfinder.neoforge.api.inventory.EquipmentInventorySource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/** Optional Curios API bridge; reads only actual equipped slots, including back slots. */
public final class CuriosInventoryReader implements EquipmentInventorySource {
    private final Function<LivingEntity, Optional<Map<String, IItemHandler>>> source;

    public CuriosInventoryReader() {
        this(optionalSource());
    }

    public CuriosInventoryReader(Function<LivingEntity, Optional<Map<String, IItemHandler>>> source) {
        this.source = source;
    }

    public Optional<List<AccessorySlot>> read(LivingEntity player) {
        return source.apply(player).map(CuriosInventoryReader::flatten);
    }

    @Override
    public String id() {
        return "curios";
    }

    static List<AccessorySlot> flatten(Map<String, IItemHandler> inventories) {
        List<AccessorySlot> result = new ArrayList<>();
        for (var entry : new TreeMap<>(inventories).entrySet()) {
            IItemHandler inventory = entry.getValue();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                result.add(new AccessorySlot(entry.getKey(), slot, inventory.getStackInSlot(slot)));
            }
        }
        return List.copyOf(result);
    }

    private static Function<LivingEntity, Optional<Map<String, IItemHandler>>> optionalSource() {
        try {
            ClassLoader loader = CuriosInventoryReader.class.getClassLoader();
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi", false, loader);
            Method inventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            Method groups = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler", false, loader)
                    .getMethod("getCurios");
            Method stacks = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler", false, loader)
                    .getMethod("getStacks");
            return new Function<>() {
                private boolean failed;

                @Override
                public Optional<Map<String, IItemHandler>> apply(LivingEntity player) {
                    if (failed) return Optional.empty();
                    try {
                        Object value = inventory.invoke(null, player);
                        if (!(value instanceof Optional<?> optional) || optional.isEmpty()) return Optional.empty();
                        Object rawGroups = groups.invoke(optional.orElseThrow());
                        if (!(rawGroups instanceof Map<?, ?> map)) return Optional.empty();
                        Map<String, IItemHandler> result = new TreeMap<>();
                        for (var entry : map.entrySet()) {
                            if (entry.getKey() instanceof String key && stacks.invoke(entry.getValue()) instanceof IItemHandler handler) {
                                result.put(key, handler);
                            }
                        }
                        return Optional.of(result);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                        failed = true;
                        LogUtils.getLogger().warn("Curios equipment capture is unavailable", exception);
                        return Optional.empty();
                    }
                }
            };
        } catch (ClassNotFoundException absent) {
            return player -> Optional.empty();
        } catch (ReflectiveOperationException | LinkageError exception) {
            LogUtils.getLogger().warn("Curios equipment API is incompatible", exception);
            return player -> Optional.empty();
        }
    }
}
