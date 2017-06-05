package org.skywalking.apm.agent.core.queue;

import com.lmax.disruptor.EventFactory;
import org.skywalking.apm.trace.TraceSegment;

/**
 * Just a holder of {@link TraceSegment} instance.
 * <p>
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

        @Override
        public TraceSegmentHolder newInstance() {
            return new TraceSegmentHolder();
        }
    }
}
