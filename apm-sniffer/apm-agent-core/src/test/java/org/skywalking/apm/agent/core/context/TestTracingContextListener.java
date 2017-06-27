package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.agent.core.context.trace.TraceSegment;

/**
 * Created by wusheng on 2017/2/19.
 */
public enum TestTracingContextListener implements TracingContextListener {
    INSTANCE;
    final TraceSegment[] finishedSegmentCarrier = {null};

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        finishedSegmentCarrier[0] = traceSegment;
    }
}
