package com.a.eye.skywalking.storage.index;

/**
 * Created by xin on 2016/11/2.
 */
public class DataFileFinder {

    private IndexL1Cache l1Cache;
    private IndexL2Cache l2Cache;

    public DataFileFinder(IndexL1Cache l1Cache, IndexL2Cache l2Cache) {
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
    }

    public long find(long timestamp) {
        Long index = l1Cache.find(timestamp);
        if (index == null) {
            index = l2Cache.find(timestamp);
        }
        return index;
    }

}
