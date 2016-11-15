package com.a.eye.skywalking.storage.block.index;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;

/**
 * Created by xin on 2016/11/2.
 */
public class BlockFinder {

    private static ILog logger = LogManager.getLogger(BlockFinder.class);

    private L1Cache l1Cache;
    private L2Cache l2Cache;

    public BlockFinder(L1Cache l1Cache, L2Cache l2Cache) {
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


    public long findLastBlockIndex() {
        return l2Cache.getLastBlockIndex();
    }

}
