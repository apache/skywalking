package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.trace.TraceSegment;

/**
 * Created by wusheng on 2017/2/19.
 */
public enum TestTracerContextListener implements TracerContextListener {
    INSTANCE;
    final TraceSegment[] finishedSegmentCarrier = {null};

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        finishedSegmentCarrier[0] = traceSegment;
    }
}
