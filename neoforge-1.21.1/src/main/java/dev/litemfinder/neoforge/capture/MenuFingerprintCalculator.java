package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.mapping.NestedContainerMapper;
import dev.litemfinder.neoforge.mapping.NestedItemContents;
import dev.litemfinder.neoforge.mapping.NestedItemContentsReader;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** Computes a cheap content signature without serializing full data components every tick. */
public final class MenuFingerprintCalculator {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private final NestedItemContentsReader contentsReader;

    public MenuFingerprintCalculator() {
        this(new NestedItemContentsReader());
    }

    public MenuFingerprintCalculator(NestedItemContentsReader contentsReader) {
        this.contentsReader = Objects.requireNonNull(contentsReader, "contentsReader must not be null");
    }

    public MenuFingerprint calculate(
            AbstractContainerMenu menu,
            MenuPartition partition,
            HolderLookup.Provider registries
    ) {
        return calculate(menu, partition, registries, List.of());
    }

    public MenuFingerprint calculate(AbstractContainerMenu menu, MenuPartition partition,
                                     HolderLookup.Provider registries, List<AccessorySlot> accessories) {
        Objects.requireNonNull(menu, "menu must not be null");
        Objects.requireNonNull(partition, "partition must not be null");
        Objects.requireNonNull(registries, "registries must not be null");
        if (!partition.supported()) {
            throw new IllegalArgumentException("cannot fingerprint an unsupported partition");
        }

        long hash = FNV_OFFSET;
        int occupied = 0;
        for (MenuSlotRef reference : partition.slots()) {
            Slot slot = menu.getSlot(reference.menuSlot());
            ItemStack stack = slot.getItem();
            hash = mix(hash, reference.containerSlot());
            hash = mix(hash, slot.x);
            hash = mix(hash, slot.y);
            if (stack.isEmpty()) {
                hash = mix(hash, 0);
                continue;
            }
            occupied++;
            hash = hashStack(hash, stack, registries, 0);
        }
        for (AccessorySlot slot : accessories) {
            for (char character : slot.key().toCharArray()) {
                hash = mix(hash, character);
            }
            hash = mix(hash, -1);
            if (slot.stack().isEmpty()) {
                hash = mix(hash, 0);
            } else {
                occupied++;
                hash = hashStack(hash, slot.stack(), registries, 0);
            }
        }
        var fluids = FluidInventoryAdapters.readMenu(menu);
        if (fluids.isPresent()) {
            long signature = fluids.orElseThrow().fingerprint();
            hash = mix(hash, 1);
            hash = mix(hash, (int) signature);
            hash = mix(hash, (int) (signature >>> 32));
        }
        return new MenuFingerprint(hash, partition.slots().size() + accessories.size(), occupied);
    }

    private long hashStack(long hash, ItemStack stack, HolderLookup.Provider registries, int depth) {
        hash = mix(hash, ItemStack.hashItemAndComponents(stack));
        hash = mix(hash, stack.getCount());
        if (contentsReader.hasCachedFluids(stack)) {
            var fluids = contentsReader.readFluids(stack, registries);
            long signature = fluids.map(dev.litemfinder.neoforge.api.inventory.FluidInventory::fingerprint).orElse(0L);
            hash = mix(hash, (int) signature);
            hash = mix(hash, (int) (signature >>> 32));
        }
        if (depth >= NestedContainerMapper.DEFAULT_MAX_DEPTH) {
            return hash;
        }
        Optional<NestedItemContents> resolved = contentsReader.read(stack, registries);
        if (resolved.isEmpty()) {
            return mix(hash, 0);
        }
        NestedItemContents contents = resolved.orElseThrow();
        hash = mix(hash, contents.slotCount());
        for (var entry : contents.stacks().entrySet()) {
            hash = mix(hash, entry.getKey());
            hash = hashStack(hash, entry.getValue(), registries, depth + 1);
        }
        return hash;
    }

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * FNV_PRIME;
    }
}
