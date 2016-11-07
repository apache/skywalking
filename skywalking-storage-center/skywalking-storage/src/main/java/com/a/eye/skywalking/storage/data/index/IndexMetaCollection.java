package com.a.eye.skywalking.storage.data.index;


import com.a.eye.skywalking.storage.block.index.BlockFinder;
import com.a.eye.skywalking.storage.block.index.BlockIndexEngine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IndexMetaCollection implements Iterable<IndexMetaInfo> {

    private List<IndexMetaInfo> metaInfo;

    public IndexMetaCollection() {
        metaInfo = new ArrayList<>();
    }

    public void add(IndexMetaInfo info) {
        metaInfo.add(info);
    }

    @Override
    public Iterator<IndexMetaInfo> iterator() {
        return metaInfo.iterator();
    }
}
