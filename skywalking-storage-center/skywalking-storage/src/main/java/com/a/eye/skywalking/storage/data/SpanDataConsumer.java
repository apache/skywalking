package com.a.eye.skywalking.storage.data;

import com.a.eye.datacarrier.consumer.IConsumer;
import com.a.eye.skywalking.storage.block.index.BlockIndexEngine;
import com.a.eye.skywalking.storage.data.file.DataFileWriter;
import com.a.eye.skywalking.storage.data.index.*;
import com.a.eye.skywalking.storage.data.spandata.SpanData;

import java.util.Iterator;
import java.util.List;

public class SpanDataConsumer implements IConsumer<SpanData> {

    private IndexDBConnectorCache cache;
    private DataFileWriter        fileWriter;

    public SpanDataConsumer() {
        cache = new IndexDBConnectorCache();
        fileWriter = new DataFileWriter();
    }

    @Override
    public void consume(List<SpanData> data) {

        Iterator<IndexMetaGroup<Long>> iterator =
                IndexMetaCollections.group(fileWriter.write(data), new GroupKeyBuilder<Long>() {
                    @Override
                    public Long buildKey(IndexMetaInfo metaInfo) {
                        return BlockIndexEngine.newFinder().find(metaInfo.getTraceStartTime());
                    }
                }).iterator();

        while (iterator.hasNext()) {
            IndexMetaGroup<Long> metaGroup = iterator.next();
            IndexOperator indexOperator = IndexOperator.newOperator(getDBConnector(metaGroup));
            indexOperator.batchUpdate(metaGroup);
        }

    }

    private IndexDBConnector getDBConnector(IndexMetaGroup<Long> metaGroup) {
        return cache.get(metaGroup.getKey());
    }

    @Override
    public void onError(List<SpanData> list, Throwable throwable) {

    }
}
