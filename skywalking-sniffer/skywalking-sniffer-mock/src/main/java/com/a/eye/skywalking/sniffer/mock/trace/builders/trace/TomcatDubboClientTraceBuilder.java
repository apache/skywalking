package com.a.eye.skywalking.sniffer.mock.trace.builders.trace;

import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.sniffer.mock.trace.TraceSegmentBuilder;
import com.a.eye.skywalking.sniffer.mock.trace.builders.span.DubboSpanGenerator;
import com.a.eye.skywalking.sniffer.mock.trace.builders.span.TomcatSpanGenerator;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * A Trace segment contains two spans with ChildOf relations,
 * the parent is a Tomcat span,
 * the child is a Dubbo client span.
 *
 * @author wusheng
 */
public enum TomcatDubboClientTraceBuilder implements TraceSegmentBuilder {
    INSTANCE;

    @Override public TraceSegment build(MockTracerContextListener listener) {
        TomcatSpanGenerator.ON200 rootSpan = new TomcatSpanGenerator.ON200();
        rootSpan.build(new DubboSpanGenerator.Client());
        rootSpan.generate();
        return listener.getFinished(0);
    }
}
