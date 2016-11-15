package com.a.eye.skywalking.storage.listener;

import com.a.eye.datacarrier.DataCarrier;
import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.listener.SpanStorageListener;
import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.SpanDataConsumer;
import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanDataBuilder;

public class StorageListener implements SpanStorageListener {

    private ILog logger = LogManager.getLogger(StorageListener.class);

    private DataCarrier<SpanData> spanDataDataCarrier;

    public StorageListener() {
        spanDataDataCarrier = new DataCarrier<>(Config.DataConsumer.CHANNEL_SIZE, Config.DataConsumer.BUFFER_SIZE);
        spanDataDataCarrier.consume(new SpanDataConsumer(), Config.DataConsumer.CONSUMER_SIZE, true);
    }

    @Override
    public boolean storage(RequestSpan requestSpan) {
        try {
            spanDataDataCarrier.produce(SpanDataBuilder.build(requestSpan));
            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.INFO,"RequestSpan stored.");
            return true;
        } catch (Exception e) {
            logger.error("RequestSpan trace-id[{}] store failure..", requestSpan.getTraceId(), e);
            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.ERROR,"RequestSpan store failure.");
            return false;
        }
    }

    @Override
    public boolean storage(AckSpan ackSpan) {
        try {
            spanDataDataCarrier.produce(SpanDataBuilder.build(ackSpan));
            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.INFO,"AckSpan stored.");
            return true;
        } catch (Exception e) {
            logger.error("AckSpan trace-id[{}] store failure..", ackSpan.getTraceId(), e);
            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.ERROR,"AckSpan store failure.");
            return false;
        }
    }
}
