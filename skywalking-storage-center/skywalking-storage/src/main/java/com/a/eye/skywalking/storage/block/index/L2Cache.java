package com.a.eye.skywalking.storage.block.index;

import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 块索引的二级缓存
 */
public class L2Cache {

    private TreeSet<Long> cacheData  = new TreeSet<Long>();
    private ReadWriteLock updateLock = new ReentrantReadWriteLock();

    void init(List<Long> data) {
        this.cacheData.addAll(cacheData);
    }

    public long find(long timestamp) {
        Lock lock = updateLock.readLock();
        try {
            lock.lock();
            return this.cacheData.higher(timestamp);
        } finally {
            lock.unlock();
        }
    }

    public void add2Rebuild(long timestamp) {
        TreeSet<Long> newCacheData = new TreeSet<>(cacheData);
        newCacheData.add(timestamp);
        Lock lock = updateLock.writeLock();

        try {
            lock.lock();
            cacheData.add(timestamp);
        } finally {
            lock.unlock();
        }
    }
}
