package com.a.eye.skywalking.api.queue;

import com.a.eye.skywalking.trace.TraceSegment;
import com.lmax.disruptor.EventFactory;

/**
 * Just a holder of {@link TraceSegment} instance.
 *
 * Created by wusheng on 2017/2/17.
 */
public final class TraceSegmentHolder {
    private TraceSegment value;

    public TraceSegment getValue() {
        return value;
    }

    public void setValue(TraceSegment value) {
        this.value = value;
    }

    public void clear() {
        this.value = null;
    }

    public enum Factory implements EventFactory<TraceSegmentHolder> {
        INSTANCE;

        @Override public TraceSegmentHolder newInstance() {
            return new TraceSegmentHolder();
        }
    }
}
