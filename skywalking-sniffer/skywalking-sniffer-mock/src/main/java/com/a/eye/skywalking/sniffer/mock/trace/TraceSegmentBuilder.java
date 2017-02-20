package com.a.eye.skywalking.sniffer.mock.trace;

import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * Created by wusheng on 2017/2/20.
 */
public interface TraceSegmentBuilder {
    TraceSegment build(MockTracerContextListener listener);
}
