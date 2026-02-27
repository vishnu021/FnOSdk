package com.vish.fno.util.tick;

import com.vish.fno.model.Ticker;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickCircularBufferTest {

    @Test
    void emptyBuffer_returnsEmptyList() {
        TickCircularBuffer buffer = new TickCircularBuffer(10);
        List<Ticker> view = buffer.asList();

        assertTrue(view.isEmpty());
        assertEquals(0, view.size());
    }

    @Test
    void singleElement_returnsCorrectly() {
        TickCircularBuffer buffer = new TickCircularBuffer(10);
        Ticker tick = createTicker(100.0);
        buffer.add(tick);

        List<Ticker> view = buffer.asList();
        assertEquals(1, view.size());
        assertEquals(100.0, view.get(0).lastTradedPrice());
    }

    @Test
    void fillBuffer_preservesInsertionOrder() {
        TickCircularBuffer buffer = new TickCircularBuffer(5);
        for (int i = 0; i < 5; i++) {
            buffer.add(createTicker(i * 10.0));
        }

        List<Ticker> view = buffer.asList();
        assertEquals(5, view.size());
        assertEquals(0.0, view.get(0).lastTradedPrice());
        assertEquals(10.0, view.get(1).lastTradedPrice());
        assertEquals(40.0, view.get(4).lastTradedPrice());
    }

    @Test
    void overflowBuffer_dropsOldestElements() {
        TickCircularBuffer buffer = new TickCircularBuffer(3);
        // Add 5 elements to a capacity-3 buffer
        for (int i = 0; i < 5; i++) {
            buffer.add(createTicker(i * 10.0));
        }

        List<Ticker> view = buffer.asList();
        assertEquals(3, view.size());
        // Should contain elements 2, 3, 4 (oldest 0 and 1 dropped)
        assertEquals(20.0, view.get(0).lastTradedPrice());
        assertEquals(30.0, view.get(1).lastTradedPrice());
        assertEquals(40.0, view.get(2).lastTradedPrice());
    }

    @Test
    void overflowBuffer_wrapsAroundMultipleTimes() {
        TickCircularBuffer buffer = new TickCircularBuffer(3);
        // Add 10 elements — wraps around 3+ times
        for (int i = 0; i < 10; i++) {
            buffer.add(createTicker(i));
        }

        List<Ticker> view = buffer.asList();
        assertEquals(3, view.size());
        // Should contain elements 7, 8, 9
        assertEquals(7.0, view.get(0).lastTradedPrice());
        assertEquals(8.0, view.get(1).lastTradedPrice());
        assertEquals(9.0, view.get(2).lastTradedPrice());
    }

    @Test
    void snapshotView_isIndependentOfFutureWrites() {
        TickCircularBuffer buffer = new TickCircularBuffer(5);
        buffer.add(createTicker(100.0));
        buffer.add(createTicker(200.0));

        // Take a snapshot
        List<Ticker> snapshot = buffer.asList();
        assertEquals(2, snapshot.size());

        // Add more after snapshot
        buffer.add(createTicker(300.0));

        // Snapshot size should still be 2 (indices captured at call time)
        assertEquals(2, snapshot.size());
        assertEquals(100.0, snapshot.get(0).lastTradedPrice());
        assertEquals(200.0, snapshot.get(1).lastTradedPrice());
    }

    @Test
    void listView_throwsOnOutOfBoundsAccess() {
        TickCircularBuffer buffer = new TickCircularBuffer(5);
        buffer.add(createTicker(100.0));

        List<Ticker> view = buffer.asList();
        assertThrows(IndexOutOfBoundsException.class, () -> view.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> view.get(-1));
    }

    @Test
    void clear_emptiesBuffer() {
        TickCircularBuffer buffer = new TickCircularBuffer(5);
        buffer.add(createTicker(100.0));
        buffer.add(createTicker(200.0));

        buffer.clear();

        assertEquals(0, buffer.size());
        assertTrue(buffer.asList().isEmpty());
    }

    @Test
    void clearAndReuse_worksCorrectly() {
        TickCircularBuffer buffer = new TickCircularBuffer(3);
        buffer.add(createTicker(100.0));
        buffer.add(createTicker(200.0));

        buffer.clear();

        buffer.add(createTicker(300.0));
        List<Ticker> view = buffer.asList();
        assertEquals(1, view.size());
        assertEquals(300.0, view.get(0).lastTradedPrice());
    }

    @Test
    void largeCapacity_matchesProductionSize() {
        TickCircularBuffer buffer = new TickCircularBuffer(500);
        for (int i = 0; i < 1000; i++) {
            buffer.add(createTicker(i));
        }

        List<Ticker> view = buffer.asList();
        assertEquals(500, view.size());
        // Oldest should be tick 500
        assertEquals(500.0, view.get(0).lastTradedPrice());
        // Newest should be tick 999
        assertEquals(999.0, view.get(499).lastTradedPrice());
    }

    private static Ticker createTicker(double price) {
        Date now = new Date();
        return new Ticker("full", false, 0L, "TEST", price,
                price, price, price, price, 0.0, 0.0, price, 0L,
                0.0, 0.0, now, 0.0, 0.0, 0.0, now, now, null);
    }
}
