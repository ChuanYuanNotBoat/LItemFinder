package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.identity.MinecraftContainerIdentityResolver;
import dev.litemfinder.neoforge.identity.ResolvedContainerIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.Objects;
import java.util.UUID;

/** Correlates a short-lived client interaction target with the menu it opens. */
public final class RecentInteractionTracker {

    private static final long MAX_AGE_TICKS = 5;

    private final MinecraftContainerIdentityResolver identities;
    private long tick;
    private Target target;

    public RecentInteractionTracker(MinecraftContainerIdentityResolver identities) {
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
    }

    public void rememberBlock(Level level, BlockPos position) {
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(position, "position must not be null");
        target = new BlockTarget(level, position.immutable(), tick);
    }

    public void rememberEntity(Entity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        target = new EntityTarget(entity, tick);
    }

    public void advanceTick() {
        tick++;
        if (target != null && tick - target.observedAt() > MAX_AGE_TICKS) {
            target = null;
        }
    }

    public ResolvedContainerIdentity resolve(String scope, Player player, AbstractContainerMenu menu) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(menu, "menu must not be null");
        if (menu instanceof InventoryMenu) {
            return identities.playerInventory(scope, player);
        }

        Target current = target;
        target = null;
        if (current != null && tick - current.observedAt() <= MAX_AGE_TICKS) {
            if (current instanceof BlockTarget block && block.level() == player.level()) {
                BlockState state = block.level().getBlockState(block.position());
                if (state.getBlock() instanceof EnderChestBlock) {
                    return identities.enderChest(scope, player);
                }
                return identities.block(scope, block.level(), normalizeChest(block.position(), state), menu);
            }
            if (current instanceof EntityTarget entity && entity.entity().level() == player.level()) {
                return identities.entity(scope, entity.entity(), menu);
            }
        }
        return identities.session(scope, UUID.randomUUID(), menu);
    }

    public void clear() {
        target = null;
    }

    static BlockPos normalizeChest(BlockPos position, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return position;
        }
        BlockPos other = position.relative(ChestBlock.getConnectedDirection(state));
        return compare(position, other) <= 0 ? position : other;
    }

    private static int compare(BlockPos left, BlockPos right) {
        int x = Integer.compare(left.getX(), right.getX());
        if (x != 0) {
            return x;
        }
        int y = Integer.compare(left.getY(), right.getY());
        return y != 0 ? y : Integer.compare(left.getZ(), right.getZ());
    }

    private sealed interface Target permits BlockTarget, EntityTarget {
        long observedAt();
    }

    private record BlockTarget(Level level, BlockPos position, long observedAt) implements Target {
    }

    private record EntityTarget(Entity entity, long observedAt) implements Target {
    }
}
