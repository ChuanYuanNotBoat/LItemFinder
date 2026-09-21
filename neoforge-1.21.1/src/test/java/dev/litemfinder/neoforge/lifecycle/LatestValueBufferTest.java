package dev.litemfinder.neoforge.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatestValueBufferTest {

    @Test
    void replacesPendingValueWithoutChangingKeyOrder() {
        LatestValueBuffer<String, Integer> buffer = new LatestValueBuffer<>(2);

        assertEquals(LatestValueBuffer.OfferResult.ADDED, buffer.offer("first", 1));
        assertEquals(LatestValueBuffer.OfferResult.ADDED, buffer.offer("second", 2));
        assertEquals(LatestValueBuffer.OfferResult.REPLACED, buffer.offer("first", 3));

        assertEquals(new LatestValueBuffer.PendingValue<>("first", 3), buffer.poll().orElseThrow());
        assertEquals(new LatestValueBuffer.PendingValue<>("second", 2), buffer.poll().orElseThrow());
        assertTrue(buffer.poll().isEmpty());
    }

    @Test
    void rejectsOnlyNewKeysAtCapacity() {
        LatestValueBuffer<String, Integer> buffer = new LatestValueBuffer<>(1);
        buffer.offer("existing", 1);

        assertEquals(LatestValueBuffer.OfferResult.REJECTED_CAPACITY, buffer.offer("new", 2));
        assertEquals(LatestValueBuffer.OfferResult.REPLACED, buffer.offer("existing", 3));
        assertEquals(List.of(new LatestValueBuffer.PendingValue<>("existing", 3)), buffer.drain());
        assertEquals(0, buffer.size());
    }
}
