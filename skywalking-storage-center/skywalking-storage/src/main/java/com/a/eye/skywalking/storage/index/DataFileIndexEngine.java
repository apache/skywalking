package com.a.eye.skywalking.storage.index;


public class DataFileIndexEngine {

    private static IndexL1Cache l1Cache;
    private static IndexL2Cache l2Cache;


    public static void start(){
        l1Cache = new IndexL1Cache();
        l2Cache = new IndexL2Cache();
        newUpdator().init();
    }

    public static DataFileFinder newFinder() {
        return new DataFileFinder(l1Cache, l2Cache);
    }


    public static DataFileIndexUpdator newUpdator() {
        return new DataFileIndexUpdator(l1Cache, l2Cache);
    }
}
