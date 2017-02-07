package com.a.eye.skywalking.routing.disruptor.ack;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.routing.alarm.SpanAlarmHandler;
import com.a.eye.skywalking.routing.config.Config;
import com.a.eye.skywalking.routing.disruptor.AbstractSpanDisruptor;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * Created by xin on 2016/11/29.
 */
public class AckSpanDisruptor extends AbstractSpanDisruptor {
    private static ILog logger = LogManager.getLogger(AckSpanDisruptor.class);
    private Disruptor<AckSpanHolder> ackSpanDisruptor;
    private RingBuffer<AckSpanHolder> ackSpanRingBuffer;

    private RouteAckSpanBufferEventHandler ackSpanEventHandler;

    public AckSpanDisruptor(String connectionURL) {
        ackSpanDisruptor = new Disruptor<AckSpanHolder>(new AckSpanFactory(), Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        ackSpanEventHandler = new RouteAckSpanBufferEventHandler(connectionURL);
        ackSpanDisruptor.handleEventsWith(ackSpanEventHandler).then(new SpanAlarmHandler()).then(new AckSpanClearEventHandler());
        ackSpanDisruptor.start();
        ackSpanRingBuffer = ackSpanDisruptor.getRingBuffer();
    }

    public boolean saveAckSpan(AckSpan ackSpan) {
        long sequence = this.getRingBufferSequence(ackSpanRingBuffer);
        if(this.isShutdown()){
            return false;
        }

        try {
            AckSpanHolder data = ackSpanRingBuffer.get(sequence);
            data.setAckSpan(ackSpan);
            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.INFO, "AckSpan stored.");
            return true;
        } catch (Exception e) {
            logger.error("AckSpan trace-id[{}] store failure..", ackSpan.getTraceId(), e);
            HealthCollector.getCurrentHeathReading("StorageListener").updateData(HeathReading.ERROR, "AckSpan store failure.");
            return false;
        } finally {
            ackSpanRingBuffer.publish(sequence);
        }
    }


    public void shutdown() {
        super.shutdown();
        ackSpanEventHandler.stop();
        ackSpanDisruptor.shutdown();
    }
}
