package com.a.eye.skywalking.storage.data;

import com.a.eye.skywalking.storage.block.index.BlockFinder;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndexMetaInfoCategory {

    public static Map<Long, List<IndexMetaInfo>> categorizeByDataIndexTime(List<IndexMetaInfo> indexMetaInfo,
            BlockFinder finder) {
        Map<Long, List<IndexMetaInfo>> categorizeMetaInfo = new HashMap<Long, List<IndexMetaInfo>>();

        for (IndexMetaInfo info : indexMetaInfo) {
            long timestamp = finder.find(info.getStartTime());

            List<IndexMetaInfo> metaInfos = categorizeMetaInfo.get(timestamp);
            if (metaInfos == null) {
                metaInfos.add(info);
                categorizeMetaInfo.put(timestamp, metaInfos);
            }
        }

        return categorizeMetaInfo;
    }
}
