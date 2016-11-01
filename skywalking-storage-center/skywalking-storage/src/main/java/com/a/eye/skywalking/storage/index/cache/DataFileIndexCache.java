package com.a.eye.skywalking.storage.index.cache;

import com.a.eye.skywalking.storage.index.DataFileIndexFinder;
import com.a.eye.skywalking.storage.index.DataFileIndexUpdator;
import com.a.eye.skywalking.storage.index.DataIndexMetaData;

public class DataFileIndexCache implements DataFileIndexFinder, DataFileIndexUpdator {

    private IndexL1Cache l1Cache;
    private IndexL2Cache l2Cache;

    public DataFileIndexCache() {
        //load the index data
    }

    @Override
    public void update(long timestamp) {
        // TODO: 2016/11/1 通知Cache更新，以及Index的文件更新
    }

    @Override
    public DataIndexMetaData find(long timestamp) {
        // TODO: 2016/11/1 查找一级缓存，二级缓存
        return null;
    }
}
