package com.a.eye.skywalking.storage.block.index;


public class BlockIndexEngine {

    private static L1Cache l1Cache;
    private static L2Cache l2Cache;

    public static void start(){
        l1Cache = new L1Cache();
        l2Cache = new L2Cache();
        newUpdator().init();
    }

    public static BlockFinder newFinder() {
        return new BlockFinder(l1Cache, l2Cache);
    }


    public static BlockIndexUpdator newUpdator() {
        return new BlockIndexUpdator(l1Cache, l2Cache);
    }
}
