package com.a.eye.skywalking.storage.data.index;


import com.a.eye.skywalking.storage.block.index.BlockFinder;
import com.a.eye.skywalking.storage.block.index.BlockIndexEngine;

import java.util.ArrayList;
import java.util.List;

public class IndexMetaCollections {

    private List<IndexMetaInfo> metaInfo;
    private BlockFinder finder = BlockIndexEngine.newFinder();

    public List<IndexMetaGroup> group() {
        List<IndexMetaGroup> indexMetaGroups = new ArrayList<IndexMetaGroup>();
        for (IndexMetaInfo info : metaInfo) {
            long timestamp = finder.find(info.getStartTime());

            int index = indexMetaGroups.indexOf(new IndexMetaGroup(timestamp));
            IndexMetaGroup metaGroup;

            if (index == -1) {
                metaGroup = new IndexMetaGroup(timestamp);
                indexMetaGroups.add(metaGroup);
            } else {
                metaGroup = indexMetaGroups.get(index);
            }

            metaGroup.addIndexMetaInfo(info);
        }

        return indexMetaGroups;
    }


}
