package com.a.eye.skywalking.disruptor;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.disruptor.ack.AckSpanFactory;
import com.a.eye.skywalking.disruptor.ack.AckSpanHolder;
import com.a.eye.skywalking.disruptor.ack.SendAckSpanEventHandler;
import com.a.eye.skywalking.disruptor.request.RequestSpanFactory;
import com.a.eye.skywalking.disruptor.request.RequestSpanHolder;
import com.a.eye.skywalking.disruptor.request.SendRequestSpanEventHandler;
import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * Created by wusheng on 2016/11/26.
 */
public class AckSpanDisruptor {
    private ILog logger = LogManager.getLogger(AckSpanDisruptor.class);

    private Disruptor<AckSpanHolder> ackSpanDisruptor;
    private RingBuffer<AckSpanHolder> ackSpanRingBuffer;

    public static final AckSpanDisruptor INSTANCE = new AckSpanDisruptor();

    private AckSpanDisruptor(){
        ackSpanDisruptor = new Disruptor<AckSpanHolder>(new AckSpanFactory(), Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        ackSpanDisruptor.handleEventsWith(new SendAckSpanEventHandler());
        ackSpanDisruptor.start();
        ackSpanRingBuffer = ackSpanDisruptor.getRingBuffer();
    }

    public void ready2Send(AckSpan ackSpan) {
        long sequence = ackSpanRingBuffer.next();  // Grab the next sequence
        try {
            AckSpanHolder data = ackSpanRingBuffer.get(sequence);
            data.setData(ackSpan);

            HealthCollector.getCurrentHeathReading("AckSpanDisruptor").updateData(HeathReading.INFO, "ready2Send stored.");
        } catch (Exception e) {
            logger.error("AckSpan trace-id[{}] ready2Send failure.", ackSpan.getTraceId(), e);
            HealthCollector.getCurrentHeathReading("AckSpanDisruptor").updateData(HeathReading.ERROR, "AckSpan ready2Send failure.");
        } finally {
            ackSpanRingBuffer.publish(sequence);
        }
    }

}
