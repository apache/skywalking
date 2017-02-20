package com.a.eye.skywalking.sniffer.mock.trace.builders.trace;

import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.sniffer.mock.trace.TraceSegmentBuilder;
import com.a.eye.skywalking.sniffer.mock.trace.builders.span.TomcatSpanGenerator;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * A Trace contains only one span, which represent a tomcat server side span.
 *
 * Created by wusheng on 2017/2/20.
 */
public enum SingleTomcat500TraceBuilder implements TraceSegmentBuilder {
    INSTANCE;

    @Override public TraceSegment build(MockTracerContextListener listener) {
        TomcatSpanGenerator.INSTANCE.on500();
        return listener.getFinished(0);
    }
}
