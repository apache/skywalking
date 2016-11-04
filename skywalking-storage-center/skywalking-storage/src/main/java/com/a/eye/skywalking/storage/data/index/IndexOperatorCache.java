package com.a.eye.skywalking.storage.data.index;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class IndexOperatorCache {

    private static final int MAX_CACHE_SIZE = 5;

    private LRUCache<Long, IndexOperator> cachedOperators;

    public IndexOperatorCache() {
        cachedOperators = new LRUCache<Long, IndexOperator>(MAX_CACHE_SIZE);
    }

    public IndexOperator get(long timestamp) {
        return cachedOperators.get(timestamp);
    }

    public void updateCache(long timestamp, IndexOperator operator) {
        cachedOperators.put(timestamp, operator);
    }

    private static class LRUCache<K, V> {
        private final int MAX_CACHE_SIZE;
        private final float DEFAULT_LOAD_FACTOR = 0.75f;
        private LinkedHashMap<K, V> map;
        private ReadWriteLock cacheLock = new ReentrantReadWriteLock();

        public LRUCache(int cacheSize) {
            MAX_CACHE_SIZE = cacheSize;
            int capacity = (int) Math.ceil(MAX_CACHE_SIZE / DEFAULT_LOAD_FACTOR) + 1;
            map = new LinkedHashMap(capacity, DEFAULT_LOAD_FACTOR, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            };
        }

        public void put(K key, V value) {
            Lock lock = cacheLock.writeLock();
            try {
                lock.lock();
                map.put(key, value);
            } finally {
                lock.unlock();
            }
        }

        public V get(K key) {
            Lock lock = cacheLock.readLock();
            try {
                lock.lock();
                return map.get(key);
            } finally {
                lock.unlock();
            }
        }

    }
}
