package com.a.eye.skywalking.storage.data;

import com.a.eye.skywalking.storage.block.index.BlockIndexEngine;
import com.a.eye.skywalking.storage.data.file.DataFileReader;
import com.a.eye.skywalking.storage.data.index.*;
import com.a.eye.skywalking.storage.data.spandata.SpanData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpanDataFinder {
    public static List<SpanData> find(String traceId) {
        long blockIndex = BlockIndexEngine.newFinder().find(fetchStartTimeFromTraceId(traceId));
        if (blockIndex == 0) {
            return new ArrayList<SpanData>();
        }
        IndexDBConnector indexDBConnector = new IndexDBConnector(blockIndex);
        IndexMetaCollection indexMetaCollection = indexDBConnector.queryByTraceId(traceId);

        Iterator<IndexMetaGroup<String>> iterator =
                IndexMetaCollections.group(indexMetaCollection, new GroupKeyBuilder<String>() {
                    @Override
                    public String buildKey(IndexMetaInfo metaInfo) {
                        return metaInfo.getFileName();
                    }
                }).iterator();

        List<SpanData> result = new ArrayList<SpanData>();
        while (iterator.hasNext()) {
            IndexMetaGroup<String> group = iterator.next();
            result.addAll(new DataFileReader(group.getKey()).read(group.getMetaInfo()));
        }

        return result;
    }

    private static long fetchStartTimeFromTraceId(String traceId) {
        String[] traceIdSegment = traceId.split("\\.");
        return Long.parseLong(traceIdSegment[traceIdSegment.length - 5]);
    }
}
