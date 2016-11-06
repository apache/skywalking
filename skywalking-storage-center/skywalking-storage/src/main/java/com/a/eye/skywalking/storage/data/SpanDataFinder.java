package com.a.eye.skywalking.storage.data;

import com.a.eye.skywalking.storage.block.index.BlockIndexEngine;
import com.a.eye.skywalking.storage.data.file.DataFileReader;
import com.a.eye.skywalking.storage.data.file.DataFileWriter;
import com.a.eye.skywalking.storage.data.index.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by xin on 2016/11/6.
 */
public class SpanDataFinder {

    public static List<byte[]> find(String traceId) {
        long blockIndex = BlockIndexEngine.newFinder().find(fetchStartTimeFromTraceId(traceId));
        IndexDBConnector indexDBConnector = new IndexDBConnector(blockIndex);
        IndexMetaCollection indexMetaCollection = indexDBConnector.queryByTraceId(traceId);

        Iterator<IndexMetaGroup<String>> iterator =
                IndexMetaCollections.group(indexMetaCollection, new GroupKeyBuilder<String>() {
                    @Override
                    public String buildKey(IndexMetaInfo metaInfo) {
                        return metaInfo.getFileName();
                    }
                }).iterator();

        List<byte[]> result = new ArrayList<byte[]>();
        while (iterator.hasNext()) {
            IndexMetaGroup<String> group = iterator.next();
            result.addAll(new DataFileReader(group.getKey()).read(group.getMetaInfo()));
        }

        return result;
    }

    private static long fetchStartTimeFromTraceId(String traceId) {
        return -1;
    }
}
