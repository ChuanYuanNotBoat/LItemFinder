package dev.litemfinder.neoforge.client.view;

import dev.litemfinder.core.model.NamespacedId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantityFormatTest {

    @Test
    void usesIntegerRemaindersForAllUnits() {
        assertEquals("2 盒 + 3 组 + 5 个", QuantityFormat.display(
                2 * 27 * 64 + 3 * 64 + 5,
                64,
                QuantityFormat.Mode.BOX_STACK_ITEM
        ));
        assertEquals("3 组 + 5 个", QuantityFormat.display(53, 16,
                QuantityFormat.Mode.STACK_AND_REMAINDER));
        assertEquals("53", QuantityFormat.display(53, 16, QuantityFormat.Mode.NUMBER));
    }

    @Test
    void clampsInputAndStepsWithoutOverflowOrNegativeCounts() {
        assertEquals(128, QuantityFormat.adjust(64, 64, 128));
        assertEquals(128, QuantityFormat.adjust(127, Long.MAX_VALUE, 128));
        assertEquals(0, QuantityFormat.adjust(2, Long.MIN_VALUE, 128));
        assertEquals(128, QuantityFormat.parseClamped("999999999999999999999999", 128));
        assertEquals(1, QuantityFormat.parseClamped("000000000000000000000001", 128));
        assertEquals(127, QuantityFormat.parseClamped("127", 128));
        assertEquals(432, QuantityFormat.unitStep(16, QuantityFormat.Unit.BOX));
        assertThrows(IllegalArgumentException.class, () -> QuantityFormat.parseClamped("-1", 128));
        assertThrows(IllegalArgumentException.class, () -> QuantityFormat.parseClamped("1.5", 128));
    }

    @Test
    void draftNeverExceedsLatestRecordedInventory() {
        AcquisitionDraft draft = new AcquisitionDraft();
        NamespacedId item = NamespacedId.parse("minecraft:diamond");
        draft.set(item, 200, 128);
        assertEquals(128, draft.get(item));
        draft.reconcile(new InventoryOverview(java.util.List.of(
                new InventoryOverview.ItemRow(item, java.util.List.of(
                        new InventoryOverview.VariantRow(
                                dev.litemfinder.core.model.ItemKey.parse("minecraft:diamond"), 64
                        )
                ))
        )));
        assertEquals(64, draft.get(item));
    }
}
