package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.InventoryReadResult;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TypedInventoryAdapterRegistryTest {
    @Test void selectsByTypePriorityAndStableIdRatherThanRegistrationOrder() throws Exception {
        for (boolean reverse : new boolean[]{false, true}) {
            var registry = new TypedInventoryAdapterRegistry<Object, String>();
            String first = reverse ? "z" : "a";
            String second = reverse ? "a" : "z";
            registry.register(id(first), Number.class, 10, value -> InventoryReadResult.available(first));
            registry.register(id(second), Number.class, 10, value -> InventoryReadResult.available(second));
            registry.register(id("unrelated"), String.class, 100, value -> { fail("wrong type"); return null; });
            assertEquals("a", registry.read(42).value().orElseThrow());
            try (var high = registry.register(id("high"), Integer.class, 20, value -> InventoryReadResult.available("high"))) {
                assertEquals("high", registry.read(42).value().orElseThrow());
            }
            assertEquals("a", registry.read(42).value().orElseThrow());
        }
    }

    @Test void distinguishesUnsupportedUnavailableAndConfirmedEmpty() throws Exception {
        var registry = new TypedInventoryAdapterRegistry<Object, String>();
        registry.register(id("generic"), Number.class, 0, value -> InventoryReadResult.available("default"));
        try (var unsupported = registry.register(id("specific"), Integer.class, 10, value -> InventoryReadResult.unsupported())) {
            assertEquals("default", registry.read(1).value().orElseThrow());
        }
        try (var unavailable = registry.register(id("specific"), Integer.class, 10, value -> InventoryReadResult.unavailable())) {
            assertEquals(InventoryReadResult.State.UNAVAILABLE, registry.read(1).state());
        }
        try (var empty = registry.register(id("specific"), Integer.class, 10, value -> InventoryReadResult.available(""))) {
            assertEquals("", registry.read(1).value().orElseThrow());
        }
        assertEquals(InventoryReadResult.State.UNSUPPORTED, registry.read("other").state());
    }

    @Test void runtimeAndLinkageFailuresCannotFallThroughToFalseEmptyButReadersCanRecover() {
        for (boolean linkage : new boolean[]{false, true}) {
            var registry = new TypedInventoryAdapterRegistry<Object, String>();
            AtomicBoolean ready = new AtomicBoolean();
            AtomicInteger fallback = new AtomicInteger();
            registry.register(id("generic"), Object.class, 0, value -> {
                fallback.incrementAndGet();
                return InventoryReadResult.available("");
            });
            registry.register(id("specific"), Integer.class, 10, value -> {
                if (ready.get()) return InventoryReadResult.available("synced");
                if (linkage) throw new NoClassDefFoundError("optional API");
                throw new IllegalStateException("not ready");
            });
            assertEquals(InventoryReadResult.State.UNAVAILABLE, registry.read(1).state());
            assertEquals(InventoryReadResult.State.UNAVAILABLE, registry.read(1).state());
            assertEquals(0, fallback.get());
            assertEquals(2, registry.failures().getFirst().count());
            ready.set(true);
            assertEquals("synced", registry.read(1).value().orElseThrow());
        }
    }

    @Test void rejectsDuplicateIdsAndOldCloseHandleCannotRemoveAReplacement() throws Exception {
        var registry = new TypedInventoryAdapterRegistry<Object, String>();
        var handle = registry.register(id("same"), Integer.class, 0, value -> InventoryReadResult.available("old"));
        assertThrows(IllegalArgumentException.class, () ->
                registry.register(id("same"), Integer.class, 0, value -> InventoryReadResult.available("bad")));
        handle.close();
        registry.register(id("same"), Integer.class, 0, value -> InventoryReadResult.available("new"));
        handle.close();
        assertEquals("new", registry.read(1).value().orElseThrow());
    }

    @Test void validatesReadResultInvariants() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryReadResult<>(InventoryReadResult.State.AVAILABLE, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryReadResult<>(InventoryReadResult.State.UNAVAILABLE, Optional.of("stale")));
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
}
