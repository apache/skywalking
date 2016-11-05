package com.a.eye.skywalking.storage.data.index;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class IndexDBConnectorCache {

    private static final int MAX_CACHE_SIZE = 5;

    private LRUCache<Long, IndexDBConnector> cachedOperators;

    public IndexDBConnectorCache() {
        cachedOperators = new LRUCache<Long, IndexDBConnector>(MAX_CACHE_SIZE);
    }

    public IndexDBConnector get(long timestamp) {
        IndexDBConnector connector = cachedOperators.get(timestamp);
        if (connector == null) {
            connector = new IndexDBConnector(timestamp);
            updateCache(timestamp, connector);
        }
        return connector;
    }

    private void updateCache(long timestamp, IndexDBConnector operator) {
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
