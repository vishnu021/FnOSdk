package com.vish.fno.model.cache;


import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@SuppressWarnings("PMD.LooseCoupling")
public class LimitedCache<K, V> {
    private final Map<K, LinkedList<V>> cache;
    private final int maxEntriesPerKey;

    public LimitedCache(int maxEntriesPerKey) {
        this.cache = new HashMap<>();
        this.maxEntriesPerKey = maxEntriesPerKey;
    }

    public void put(K key, V value) {
        LinkedList<V> values = cache.computeIfAbsent(key, k -> new LinkedList<>());

        if (values.size() >= maxEntriesPerKey) {
            values.removeFirst();
        }

        values.add(value);
    }

    public List<V> get(K key) {
        return cache.getOrDefault(key, new LinkedList<>());
    }

    public Set<K> keySet() {
        return cache.keySet();
    }

    public int size(K key) {
        return cache.getOrDefault(key, new LinkedList<>()).size();
    }
}

