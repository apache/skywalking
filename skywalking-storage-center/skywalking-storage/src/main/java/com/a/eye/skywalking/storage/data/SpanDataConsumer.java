package com.a.eye.skywalking.storage.data;

import com.a.eye.datacarrier.consumer.IConsumer;
import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.storage.block.index.BlockIndexEngine;
import com.a.eye.skywalking.storage.data.file.DataFileWriter;
import com.a.eye.skywalking.storage.data.index.*;
import com.a.eye.skywalking.storage.data.spandata.SpanData;

import java.util.Iterator;
import java.util.List;

public class SpanDataConsumer implements IConsumer<SpanData> {

    private static ILog logger = LogManager.getLogger(SpanDataConsumer.class);
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
            HealthCollector.getCurrentHeathReading("SpanDataConsumer")
                    .updateData(HeathReading.INFO, "%s messages were successful consumed .", data.size());
        }
    }

    private IndexDBConnector getDBConnector(IndexMetaGroup<Long> metaGroup) {
        return cache.get(metaGroup.getKey());
    }

    @Override
    public void onError(List<SpanData> span, Throwable throwable) {
        logger.error("Failed to consumer span data.", throwable);
        HealthCollector.getCurrentHeathReading("SpanDataConsumer").updateData(HeathReading.ERROR,
                "Failed to consume span data. error message : " + throwable.getMessage());
    }

    @Override
    public void onExit() {
        cache.close();
        fileWriter.close();
    }
}
