package dev.litemfinder.neoforge.identity;

import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.WorldLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerIdentityFactoryTest {

    private static final String SCOPE = "scope:v1:abc";
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void playerAndEnderIdentitiesAreStableLogicalContainers() {
        var inventory = ContainerIdentityFactory.playerInventory(SCOPE, PLAYER);
        var ender = ContainerIdentityFactory.enderChest(SCOPE, PLAYER);

        assertEquals(IdentityConfidence.LOGICAL, inventory.confidence());
        assertTrue(inventory.persistable());
        assertInstanceOf(LogicalLocation.class, inventory.container().location());
        assertFalse(inventory.container().id().equals(ender.container().id()));
    }

    @Test
    void blockIdentityCarriesExactWorldLocationAndMenuType() {
        NamespacedId dimension = NamespacedId.parse("minecraft:overworld");
        NamespacedId menu = NamespacedId.parse("minecraft:generic_9x3");

        var resolved = ContainerIdentityFactory.block(SCOPE, dimension, 10, 64, -3, menu);

        assertEquals(IdentityConfidence.EXACT, resolved.confidence());
        assertEquals(new WorldLocation(SCOPE, dimension, 10, 64, -3), resolved.container().location());
        assertEquals(menu, resolved.container().type().id());
        assertTrue(resolved.container().id().value().contains("10,64,-3"));
    }

    @Test
    void blockIdentityCanRetainExpectedMinecraftBlockForDestructionChecks() {
        var resolved = ContainerIdentityFactory.block(
                SCOPE,
                NamespacedId.parse("minecraft:overworld"),
                1,
                2,
                3,
                NamespacedId.parse("minecraft:generic_9x3"),
                NamespacedId.parse("minecraft:trapped_chest")
        );

        assertEquals("minecraft:trapped_chest", resolved.container().metadata().get("minecraftBlock"));
    }

    @Test
    void sessionFallbackIsExplicitlyNotPersistable() {
        var resolved = ContainerIdentityFactory.session(
                SCOPE,
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                NamespacedId.parse("example:remote_menu")
        );

        assertEquals(IdentityConfidence.SESSION_ONLY, resolved.confidence());
        assertFalse(resolved.persistable());
    }
}
