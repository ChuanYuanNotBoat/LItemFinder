package dev.litemfinder.neoforge.identity;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.WorldLocation;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Creates the stable container identities selected by the NeoForge adapter policy. */
public final class ContainerIdentityFactory {

    private static final ContainerType PLAYER_INVENTORY = type("minecraft", "player_inventory");
    private static final ContainerType ENDER_CHEST = type("minecraft", "ender_chest");

    private ContainerIdentityFactory() {
    }

    public static ResolvedContainerIdentity playerInventory(String scope, UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        String key = "player:" + requireScope(scope) + ":" + playerId + ":inventory";
        return logical(key, scope, PLAYER_INVENTORY, IdentityConfidence.LOGICAL);
    }

    public static ResolvedContainerIdentity enderChest(String scope, UUID playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        String key = "player:" + requireScope(scope) + ":" + playerId + ":ender_chest";
        return logical(key, scope, ENDER_CHEST, IdentityConfidence.LOGICAL);
    }

    public static ResolvedContainerIdentity block(
            String scope,
            NamespacedId dimension,
            int x,
            int y,
            int z,
            NamespacedId menuType
    ) {
        String checkedScope = requireScope(scope);
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(menuType, "menuType must not be null");
        String id = "block:" + checkedScope + ":" + dimension + ":" + x + "," + y + "," + z + ":" + menuType;
        ContainerRecord record = new ContainerRecord(
                new ContainerId(id),
                new ContainerType(menuType),
                new WorldLocation(checkedScope, dimension, x, y, z),
                Map.of("identityConfidence", IdentityConfidence.EXACT.name())
        );
        return new ResolvedContainerIdentity(record, IdentityConfidence.EXACT);
    }

    public static ResolvedContainerIdentity entity(
            String scope,
            NamespacedId dimension,
            UUID entityId,
            NamespacedId menuType
    ) {
        String checkedScope = requireScope(scope);
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(menuType, "menuType must not be null");
        String id = "entity:" + checkedScope + ":" + dimension + ":" + entityId;
        return logical(id, checkedScope, new ContainerType(menuType), IdentityConfidence.EXACT);
    }

    public static ResolvedContainerIdentity session(
            String scope,
            UUID sessionId,
            NamespacedId menuType
    ) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(menuType, "menuType must not be null");
        String id = "session:" + requireScope(scope) + ":" + sessionId + ":" + menuType;
        return logical(id, scope, new ContainerType(menuType), IdentityConfidence.SESSION_ONLY);
    }

    private static ResolvedContainerIdentity logical(
            String id,
            String scope,
            ContainerType type,
            IdentityConfidence confidence
    ) {
        ContainerRecord record = new ContainerRecord(
                new ContainerId(id),
                type,
                new LogicalLocation(requireScope(scope), id),
                Map.of("identityConfidence", confidence.name())
        );
        return new ResolvedContainerIdentity(record, confidence);
    }

    private static String requireScope(String scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        if (scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        return scope;
    }

    private static ContainerType type(String namespace, String path) {
        return new ContainerType(new NamespacedId(namespace, path));
    }
}
