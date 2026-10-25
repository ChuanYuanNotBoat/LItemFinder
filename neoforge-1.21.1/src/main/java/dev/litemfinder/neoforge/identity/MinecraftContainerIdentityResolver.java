package dev.litemfinder.neoforge.identity;

import dev.litemfinder.neoforge.mapping.MinecraftIds;
import dev.litemfinder.neoforge.mapping.MinecraftBlockStateMetadata;
import dev.litemfinder.core.model.NamespacedId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/** Converts confirmed Minecraft targets into stable adapter identities. */
public final class MinecraftContainerIdentityResolver {

    public ResolvedContainerIdentity playerInventory(String scope, Player player) {
        Objects.requireNonNull(player, "player must not be null");
        return ContainerIdentityFactory.playerInventory(scope, player.getUUID());
    }

    public ResolvedContainerIdentity enderChest(String scope, Player player) {
        Objects.requireNonNull(player, "player must not be null");
        return ContainerIdentityFactory.enderChest(scope, player.getUUID());
    }

    public ResolvedContainerIdentity block(
            String scope,
            Level level,
            BlockPos position,
            AbstractContainerMenu menu
    ) {
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(menu, "menu must not be null");
        var state = level.getBlockState(position);
        ResolvedContainerIdentity identity = ContainerIdentityFactory.block(
                scope,
                MinecraftIds.toCore(level.dimension().location()),
                position.getX(),
                position.getY(),
                position.getZ(),
                MinecraftIds.toCore(BuiltInRegistries.MENU.getKey(menu.getType())),
                MinecraftIds.toCore(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
        );
        return new ResolvedContainerIdentity(
                MinecraftBlockStateMetadata.withState(identity.container(), state), identity.confidence());
    }

    public ResolvedContainerIdentity entity(String scope, Entity entity, AbstractContainerMenu menu) {
        Objects.requireNonNull(entity, "entity must not be null");
        Objects.requireNonNull(menu, "menu must not be null");
        return ContainerIdentityFactory.entity(
                scope,
                MinecraftIds.toCore(entity.level().dimension().location()),
                entity.getUUID(),
                MinecraftIds.toCore(BuiltInRegistries.MENU.getKey(menu.getType()))
        );
    }

    /** A stable source type for physical block storage that has no item-slot menu. */
    public ResolvedContainerIdentity blockInventory(String scope, Level level, BlockPos position) {
        var state = level.getBlockState(position);
        var identity = ContainerIdentityFactory.block(scope, MinecraftIds.toCore(level.dimension().location()),
                position.getX(), position.getY(), position.getZ(),
                new NamespacedId("litemfinder", "block_inventory"),
                MinecraftIds.toCore(BuiltInRegistries.BLOCK.getKey(state.getBlock())));
        return new ResolvedContainerIdentity(
                MinecraftBlockStateMetadata.withState(identity.container(), state), identity.confidence());
    }

    public ResolvedContainerIdentity session(String scope, UUID sessionId, AbstractContainerMenu menu) {
        Objects.requireNonNull(menu, "menu must not be null");
        return ContainerIdentityFactory.session(
                scope,
                sessionId,
                MinecraftIds.toCore(BuiltInRegistries.MENU.getKey(menu.getType()))
        );
    }
}
