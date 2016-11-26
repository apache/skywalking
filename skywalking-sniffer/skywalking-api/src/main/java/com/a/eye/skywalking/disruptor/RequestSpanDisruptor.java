package com.a.eye.skywalking.disruptor;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.disruptor.request.RequestSpanFactory;
import com.a.eye.skywalking.disruptor.request.RequestSpanHolder;
import com.a.eye.skywalking.disruptor.request.SendRequestSpanEventHandler;
import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * Created by wusheng on 2016/11/26.
 */
public class RequestSpanDisruptor {
    private ILog logger = LogManager.getLogger(RequestSpanDisruptor.class);

    private Disruptor<RequestSpanHolder>  requestSpanDisruptor;
    private RingBuffer<RequestSpanHolder> requestSpanRingBuffer;

    public static final RequestSpanDisruptor INSTANCE = new RequestSpanDisruptor();

    private RequestSpanDisruptor(){
        requestSpanDisruptor = new Disruptor<RequestSpanHolder>(new RequestSpanFactory(), Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        requestSpanDisruptor.handleEventsWith(new SendRequestSpanEventHandler());
        requestSpanDisruptor.start();
        requestSpanRingBuffer = requestSpanDisruptor.getRingBuffer();
    }

    public void ready2Send(RequestSpan requestSpan) {
        long sequence = requestSpanRingBuffer.next();  // Grab the next sequence
        try {
            RequestSpanHolder data = requestSpanRingBuffer.get(sequence);
            data.setData(requestSpan);

            HealthCollector.getCurrentHeathReading("RequestSpanDisruptor").updateData(HeathReading.INFO, "ready2Send stored.");
        } catch (Exception e) {
            logger.error("RequestSpan trace-id[{}] ready2Send failure.", requestSpan.getTraceId(), e);
            HealthCollector.getCurrentHeathReading("RequestSpanDisruptor").updateData(HeathReading.ERROR, "RequestSpan ready2Send failure.");
        } finally {
            requestSpanRingBuffer.publish(sequence);
        }
    }
}
