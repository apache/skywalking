package com.a.eye.skywalking.storage.listener;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.listener.SpanStorageListener;
import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.spandata.AckSpanData;
import com.a.eye.skywalking.storage.data.spandata.RequestSpanData;
import com.a.eye.skywalking.storage.disruptor.ack.AckSpanFactory;
import com.a.eye.skywalking.storage.disruptor.ack.StoreAckSpanEventHandler;
import com.a.eye.skywalking.storage.disruptor.request.RequestSpanFactory;
import com.a.eye.skywalking.storage.disruptor.request.StoreRequestSpanEventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class StorageListener implements SpanStorageListener {

    private ILog logger = LogManager.getLogger(StorageListener.class);

    private Disruptor<RequestSpanData>  requestSpanDisruptor;
    private RingBuffer<RequestSpanData> requestSpanRingBuffer;

    private Disruptor<AckSpanData>  ackSpanDisruptor;
    private RingBuffer<AckSpanData> ackSpanRingBuffer;

    public StorageListener() {
        requestSpanDisruptor = new Disruptor<RequestSpanData>(new RequestSpanFactory(), Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        requestSpanDisruptor.handleEventsWith(new StoreRequestSpanEventHandler());
        requestSpanRingBuffer = requestSpanDisruptor.getRingBuffer();

        ackSpanDisruptor = new Disruptor<AckSpanData>(new AckSpanFactory(), Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        ackSpanDisruptor.handleEventsWith(new StoreAckSpanEventHandler());
        ackSpanRingBuffer = ackSpanDisruptor.getRingBuffer();
    }

    @Override
    public boolean storage(RequestSpan requestSpan) {
        long sequence = requestSpanRingBuffer.next();  // Grab the next sequence
        try {
            RequestSpanData data = requestSpanRingBuffer.get(sequence);
            data.setRequestSpan(requestSpan);

            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.INFO, "RequestSpan stored.");
            return true;
        } catch (Exception e) {
            logger.error("RequestSpan trace-id[{}] store failure..", requestSpan.getTraceId(), e);
            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.ERROR, "RequestSpan store failure.");
            return false;
        } finally{
            requestSpanRingBuffer.publish(sequence);
        }
    }

    @Override
    public boolean storage(AckSpan ackSpan) {
        long sequence = ackSpanRingBuffer.next();  // Grab the next sequence
        try {
            AckSpanData data = ackSpanRingBuffer.get(sequence);
            data.setAckSpan(ackSpan);

            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.INFO, "AckSpan stored.");
            return true;
        } catch (Exception e) {
            logger.error("AckSpan trace-id[{}] store failure..", ackSpan.getTraceId(), e);
            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.ERROR, "AckSpan store failure.");
            return false;
        } finally{
            requestSpanRingBuffer.publish(sequence);
        }
    }
}
