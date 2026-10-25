package dev.litemfinder.neoforge.compat;

import dev.litemfinder.neoforge.capture.*;
import dev.litemfinder.neoforge.mapping.*;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import dev.litemfinder.core.model.NamespacedId;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import dev.litemfinder.neoforge.identity.MinecraftContainerIdentityResolver;
import dev.litemfinder.neoforge.identity.IdentityConfidence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImmersiveEngineeringCrateAdapterTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }
    @Test void clientCrateRecordsRedstoneAndIgnoresPlayerAndUnrelatedBackingSlots() throws Exception {
        var player = new Inventory(null);
        player.setItem(0, new ItemStack(Items.DIAMOND, 64));
        var menu = new Crate(player);
        menu.container.setItem(8, new ItemStack(Items.REDSTONE, 2));
        var adapter = new ImmersiveEngineeringCrateAdapter(Crate.class);
        assertTrue(adapter.resolve(menu).orElseThrow().position().isEmpty(), "no invented zero-coordinate owner");
        try (var registration = StorageMenuAdapters.register(adapter)) {
            var partition = new VanillaMenuSlotPartitioner(true).partition(menu, player);
            assertEquals(27, partition.slots().size());
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var identity = ContainerIdentityFactory.block("crate-test", NamespacedId.parse("minecraft:overworld"),
                    1, 64, 2, NamespacedId.parse("immersiveengineering:crate"));
            var snapshot = new MenuSnapshotMapper(new CachedItemTagResolver()).map(new MenuCaptureRequest(identity, menu,
                    partition, new MenuFingerprintCalculator().calculate(menu, partition, registries), CaptureReason.OPEN_STABLE),
                    registries, Instant.EPOCH);
            assertEquals(1, snapshot.slots().size());
            assertEquals("minecraft:redstone", snapshot.slots().getFirst().stack().item().itemId().toString());
            assertEquals(2, snapshot.slots().getFirst().stack().count());
            Level level = mock(Level.class);
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.getBlockState(new BlockPos(1, 64, 2))).thenReturn(Blocks.BARREL.defaultBlockState());
            Player wearer = mock(Player.class);
            when(wearer.level()).thenReturn(level);
            var interactions = new RecentInteractionTracker(new MinecraftContainerIdentityResolver());
            interactions.rememberBlock(level, new BlockPos(1, 64, 2));
            var resolved = interactions.resolve("crate-test", wearer, menu);
            assertEquals(IdentityConfidence.EXACT, resolved.confidence());
            assertEquals(new dev.litemfinder.core.model.WorldLocation("crate-test", NamespacedId.parse("minecraft:overworld"),
                    1, 64, 2), resolved.container().location());
        }
    }
    public static class Crate extends AbstractContainerMenu {
        private final Container container = new SimpleContainer(27);
        Crate(Inventory player) {
            super(MenuType.GENERIC_9x3, 1);
            for (int slot = 0; slot < 27; slot++) addSlot(new Slot(container, slot, 8 + slot % 9 * 18, 18 + slot / 9 * 18));
            addSlot(new Slot(player, 0, 8, 90));
            addSlot(new Slot(new SimpleContainer(new ItemStack(Items.EMERALD)), 0, 8, 108));
        }
        public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        public boolean stillValid(Player player) { return true; }
    }
}
