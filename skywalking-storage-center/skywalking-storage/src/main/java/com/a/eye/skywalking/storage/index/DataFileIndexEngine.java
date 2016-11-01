package com.a.eye.skywalking.storage.index;

import com.a.eye.skywalking.storage.index.cache.DataFileIndexCache;

/**
 * 用于初始化DataFileIndex的数据，并且提供操作Index的所有类
 */
public class DataFileIndexEngine {

    private static final DataFileIndexCache indexCache;

    static {

        indexCache = new DataFileIndexCache();
    }

    private DataFileIndexEngine() {
        //DO Nothing
    }

    public static DataFileIndexFinder newFinder() {
        return indexCache;
    }

    public static DataFileIndexUpdator newUpdator() {
        return indexCache;
    }
}
