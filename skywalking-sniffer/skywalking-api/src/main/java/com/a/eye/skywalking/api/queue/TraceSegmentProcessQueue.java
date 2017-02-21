package com.a.eye.skywalking.api.queue;

import com.a.eye.skywalking.api.conf.Config;
import com.a.eye.skywalking.api.context.TracerContext;
import com.a.eye.skywalking.api.context.TracerContextListener;
import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
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
public enum TraceSegmentProcessQueue implements TracerContextListener {
    INSTANCE {
        @Override public void afterFinished(TraceSegment traceSegment) {
            long sequence = this.buffer.next();  // Grab the next sequence
            try {
                TraceSegmentHolder data = this.buffer.get(sequence);
                data.setValue(traceSegment);

                HealthCollector.getCurrentHeathReading("TraceSegmentProcessQueue").updateData(HeathReading.INFO, "receive finished traceSegment.");
            } finally {
                this.buffer.publish(sequence);
            }
        }
    };

    private Disruptor<TraceSegmentHolder> disruptor;
    RingBuffer<TraceSegmentHolder> buffer;

    TraceSegmentProcessQueue() {
        disruptor = new Disruptor<TraceSegmentHolder>(TraceSegmentHolder.Factory.INSTANCE, Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        buffer = disruptor.getRingBuffer();
    }

    public void start() {
        TracerContext.ListenerManager.add(this);
        disruptor.start();

    }
}
