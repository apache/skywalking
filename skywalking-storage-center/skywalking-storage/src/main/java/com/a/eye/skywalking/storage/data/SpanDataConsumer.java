package com.a.eye.skywalking.storage.data;

import com.a.eye.datacarrier.consumer.IConsumer;
import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.storage.data.file.DataFileWriter;
import com.a.eye.skywalking.storage.data.index.IndexMetaCollection;
import com.a.eye.skywalking.storage.data.index.IndexOperator;
import com.a.eye.skywalking.storage.data.index.IndexOperatorFactory;
import com.a.eye.skywalking.storage.data.spandata.SpanData;

import java.util.List;

public class SpanDataConsumer implements IConsumer<SpanData> {

    private static ILog logger = LogManager.getLogger(SpanDataConsumer.class);
    private DataFileWriter fileWriter;

    @Override
    public void init() {
        fileWriter = new DataFileWriter();
    }

    @Override
    public void consume(List<SpanData> data) {
        IndexMetaCollection collection = fileWriter.write(data);

        IndexOperator operator = IndexOperatorFactory.createIndexOperator();
        operator.batchUpdate(collection);

        HealthCollector.getCurrentHeathReading("SpanDataConsumer")
                .updateData(HeathReading.INFO, "%s messages were successful consumed .", data.size());
    }

    @Override
    public void onError(List<SpanData> span, Throwable throwable) {
        logger.error("Failed to consumer span data.", throwable);
        HealthCollector.getCurrentHeathReading("SpanDataConsumer").updateData(HeathReading.ERROR,
                "Failed to consume span data. error message : " + throwable.getMessage());
    }

    @Override
    public void onExit() {
        fileWriter.close();
    }
}
