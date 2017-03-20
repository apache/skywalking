package com.a.eye.skywalking.api.queue;

import com.a.eye.skywalking.api.boot.StatusBootService;
import com.a.eye.skywalking.api.conf.Config;
import com.a.eye.skywalking.api.context.TracerContext;
import com.a.eye.skywalking.api.context.TracerContextListener;
import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.trace.TraceSegment;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import java.util.LinkedList;
import java.util.List;

/**
 * {@link TraceSegmentProcessQueue} is a proxy of {@link Disruptor}, High Performance Inter-Thread MQ.
 *
 * {@see https://github.com/LMAX-Exchange/disruptor}
 *
 * Created by wusheng on 2017/2/17.
 */
public class TraceSegmentProcessQueue extends StatusBootService implements TracerContextListener, EventHandler<TraceSegmentHolder> {
    private static ILog logger = LogManager.getLogger(TraceSegmentProcessQueue.class);

    private Disruptor<TraceSegmentHolder> disruptor;
    private RingBuffer<TraceSegmentHolder> buffer;
    private TraceSegment[] secondLevelCache;
    private volatile int cacheIndex;

    public TraceSegmentProcessQueue() {
        disruptor = new Disruptor<TraceSegmentHolder>(TraceSegmentHolder.Factory.INSTANCE, Config.Disruptor.BUFFER_SIZE, DaemonThreadFactory.INSTANCE);
        secondLevelCache = new TraceSegment[Config.Disruptor.BUFFER_SIZE];
        cacheIndex = 0;
        disruptor.handleEventsWith(this);
        buffer = disruptor.getRingBuffer();
    }

    @Override
    protected void bootUpWithStatus() {
        TracerContext.ListenerManager.add(this);
        disruptor.start();
    }

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        if (isStarted()) {
            long sequence = this.buffer.next();  // Grab the next sequence
            try {
                TraceSegmentHolder data = this.buffer.get(sequence);
                data.setValue(traceSegment);
            } finally {
                this.buffer.publish(sequence);
            }
        }
    }

    @Override
    public void onEvent(TraceSegmentHolder event, long sequence, boolean endOfBatch) throws Exception {
        TraceSegment traceSegment = event.getValue();
        try {
            if (secondLevelCache[cacheIndex] == null) {
                secondLevelCache[cacheIndex] = traceSegment;
            }else{
                /**
                 * If your application has very high throughput(also called tps/qps),
                 * this log message will be output in very high frequency.
                 * And this is not suppose to happen. Disable log.warn or expend {@link Config.Disruptor.BUFFER_SIZE}
                 */
                logger.warn("TraceSegmentProcessQueue has data conflict. Discard the new TraceSegment.");
            }
            /**
             * increase the {@link #cacheIndex}, if it is out of range, reset it.
             */
            cacheIndex++;
            if (cacheIndex == secondLevelCache.length) {
                cacheIndex = 0;
            }
        } finally {
            event.clear();
        }
    }

    public List<TraceSegment> getCachedTraceSegments(){
        List<TraceSegment> segmentList = new LinkedList<TraceSegment>();
        for (int i = 0; i < secondLevelCache.length; i++) {
            TraceSegment segment = secondLevelCache[i];
            if(segment != null){
                segmentList.add(segment);
                secondLevelCache[i] = null;
            }
        }
        return segmentList;
    }
}
