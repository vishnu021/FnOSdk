package com.vish.fno.util.helper;

import com.vish.fno.model.Ticker;

import java.util.AbstractList;
import java.util.List;

/**
 * Lock-free, zero-allocation circular buffer for tick data.
 *
 * <p>Replaces {@code ConcurrentLinkedDeque<Ticker>} in {@link AbstractDataCache}:
 * <ul>
 *   <li>Pre-allocated {@code Ticker[]} array — no per-tick Node allocation</li>
 *   <li>{@link #asList()} returns a lightweight view — no element copying</li>
 *   <li>O(1) {@code add}, O(1) random access via {@code get(index)}</li>
 * </ul>
 *
 * <p>Thread safety: single-writer (tick ingestion thread), multiple-reader (strategy threads).
 * Write index advances monotonically; readers see a consistent snapshot because:
 * <ul>
 *   <li>Array element writes are visible after the volatile write index update</li>
 *   <li>Readers compute their view based on the write index at call time</li>
 * </ul>
 */
class TickCircularBuffer {

    private final Ticker[] buffer;
    private final int capacity;
    private volatile int writeIndex;
    private volatile int count;

    TickCircularBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new Ticker[capacity];
        this.writeIndex = 0;
        this.count = 0;
    }

    /**
     * Append a tick to the buffer, overwriting the oldest entry when full.
     * O(1), zero allocation.
     *
     * @param tick the tick to add
     */
    void add(Ticker tick) {
        buffer[writeIndex] = tick;
        writeIndex = (writeIndex + 1) % capacity;
        if (count < capacity) {
            count++;
        }
    }

    /**
     * Returns a lightweight, unmodifiable {@link List} view of the buffer contents
     * in insertion order (oldest first, newest last).
     *
     * <p>The view captures the current write index at call time, providing a
     * consistent snapshot. No elements are copied — {@code get(i)} reads directly
     * from the backing array.
     *
     * <p>Strategies use only {@code size()}, {@code get(index)}, and {@code isEmpty()},
     * all of which are O(1) on this view.
     *
     * @return an unmodifiable List view of the buffer contents
     */
    List<Ticker> asList() {
        // Capture volatile fields once for a consistent snapshot
        int snapshotCount = this.count;
        int snapshotWriteIndex = this.writeIndex;

        return new CircularBufferView(snapshotCount, snapshotWriteIndex);
    }

    /**
     * Returns the number of ticks currently in the buffer.
     */
    int size() {
        return count;
    }

    /**
     * Removes all ticks from the buffer.
     */
    void clear() {
        // Null out references to allow GC of old Ticker objects
        for (int i = 0; i < capacity; i++) {
            buffer[i] = null;
        }
        count = 0;
        writeIndex = 0;
    }

    /**
     * AbstractList-backed view that maps logical indices to physical array positions.
     *
     * <p>Logical index 0 = oldest tick, logical index (size-1) = newest tick.
     * Physical position = (writeIndex - count + logicalIndex) mod capacity.
     */
    private class CircularBufferView extends AbstractList<Ticker> {
        private final int snapshotCount;
        private final int startIndex;

        CircularBufferView(int snapshotCount, int snapshotWriteIndex) {
            this.snapshotCount = snapshotCount;
            // Start of the oldest element
            this.startIndex = (snapshotWriteIndex - snapshotCount + capacity) % capacity;
        }

        @Override
        public Ticker get(int index) {
            if (index < 0 || index >= snapshotCount) {
                throw new IndexOutOfBoundsException(
                        "Index: " + index + ", Size: " + snapshotCount);
            }
            return buffer[(startIndex + index) % capacity];
        }

        @Override
        public int size() {
            return snapshotCount;
        }
    }
}
