package com.vish.fno.model.cache;


import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@SuppressWarnings("PMD.LooseCoupling")
public class LimitedCache<K, V> {
    private final Map<K, LinkedList<V>> cache;
    private final int maxEntriesPerKey;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public LimitedCache(int maxEntriesPerKey) {
        this.cache = new ConcurrentHashMap<>();
        this.maxEntriesPerKey = maxEntriesPerKey;
    }

    /**
     * Adds a value to the cache for the given key.
     * Thread-safe operation that maintains FIFO ordering with size limit.
     *
     * @param key the cache key
     * @param value the value to add
     */
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            LinkedList<V> values = cache.computeIfAbsent(key, k -> new LinkedList<>());

            if (values.size() >= maxEntriesPerKey) {
                values.removeFirst();
            }

            values.add(value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves all values for the given key.
     * Returns a defensive copy to prevent external modification.
     *
     * @param key the cache key
     * @return defensive copy of values list
     */
    public List<V> get(K key) {
        lock.readLock().lock();
        try {
            return new ArrayList<>(cache.getOrDefault(key, new LinkedList<>()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns an unmodifiable view of the cache keys.
     *
     * @return unmodifiable set of keys
     */
    public Set<K> keySet() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    /**
     * Returns the number of entries for the given key.
     *
     * @param key the cache key
     * @return number of entries
     */
    public int size(K key) {
        lock.readLock().lock();
        try {
            return cache.getOrDefault(key, new LinkedList<>()).size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
