package dev.litemfinder.neoforge.capture;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Computes a cheap content signature without serializing full data components every tick. */
public final class MenuFingerprintCalculator {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public MenuFingerprint calculate(AbstractContainerMenu menu, MenuPartition partition) {
        Objects.requireNonNull(menu, "menu must not be null");
        Objects.requireNonNull(partition, "partition must not be null");
        if (!partition.supported()) {
            throw new IllegalArgumentException("cannot fingerprint an unsupported partition");
        }

        long hash = FNV_OFFSET;
        int occupied = 0;
        for (MenuSlotRef reference : partition.slots()) {
            Slot slot = menu.getSlot(reference.menuSlot());
            ItemStack stack = slot.getItem();
            hash = mix(hash, reference.containerSlot());
            if (stack.isEmpty()) {
                hash = mix(hash, 0);
                continue;
            }
            occupied++;
            hash = mix(hash, ItemStack.hashItemAndComponents(stack));
            hash = mix(hash, stack.getCount());
        }
        return new MenuFingerprint(hash, partition.slots().size(), occupied);
    }

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * FNV_PRIME;
    }
}
