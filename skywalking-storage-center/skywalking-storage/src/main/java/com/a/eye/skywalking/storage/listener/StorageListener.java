package com.a.eye.skywalking.storage.listener;

import com.a.eye.datacarrier.DataCarrier;
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
        spanDataDataCarrier = new DataCarrier<>(Config.Server.CHANNEL_SIZE, Config.Server.BUFFER_SIZE);
        spanDataDataCarrier.consume(new SpanDataConsumer(), 5, true);
    }

    @Override
    public boolean storage(RequestSpan requestSpan) {
        try {
            spanDataDataCarrier.produce(SpanDataBuilder.build(requestSpan));
            return true;
        } catch (Exception e) {
            logger.error("Failed to storage request span. Span Data:\n {}.", requestSpan.toByteString(), e);
            return false;
        }
    }

    @Override
    public boolean storage(AckSpan ackSpan) {
        try {
            spanDataDataCarrier.produce(SpanDataBuilder.build(ackSpan));
            return true;
        } catch (Exception e) {
            logger.error("Failed to storage ack span. ack Data:\n {}.", ackSpan.toByteString(), e);
            return false;
        }
    }
}
