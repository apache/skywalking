package com.a.eye.skywalking.api.queue;

import com.a.eye.skywalking.api.boot.StatusBootService;
import com.a.eye.skywalking.api.conf.Config;
import com.a.eye.skywalking.api.context.TracerContext;
import com.a.eye.skywalking.api.context.TracerContextListener;
import com.a.eye.skywalking.trace.TraceSegment;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * {@link TraceSegmentProcessQueue} is a proxy of {@link Disruptor}, High Performance Inter-Thread MQ.
 *
 * {@see https://github.com/LMAX-Exchange/disruptor}
 *
 * Created by wusheng on 2017/2/17.
 */
public class TraceSegmentProcessQueue extends StatusBootService implements TracerContextListener {
    private Disruptor<TraceSegmentHolder> disruptor;
    private RingBuffer<TraceSegmentHolder> buffer;

    public TraceSegmentProcessQueue() {
        disruptor = new Disruptor<>(TraceSegmentHolder.Factory.INSTANCE, Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        buffer = disruptor.getRingBuffer();
    }

    @Override
    protected void bootUpWithStatus() {
        TracerContext.ListenerManager.add(this);
        disruptor.start();
    }

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        if(isStarted()) {
            long sequence = this.buffer.next();  // Grab the next sequence
            try {
                TraceSegmentHolder data = this.buffer.get(sequence);
                data.setValue(traceSegment);
            } finally {
                this.buffer.publish(sequence);
            }
        }
    }
}
