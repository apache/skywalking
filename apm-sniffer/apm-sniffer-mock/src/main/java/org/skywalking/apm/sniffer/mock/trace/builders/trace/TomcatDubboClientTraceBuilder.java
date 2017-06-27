package org.skywalking.apm.sniffer.mock.trace.builders.trace;

import org.skywalking.apm.sniffer.mock.context.MockTracingContextListener;
import org.skywalking.apm.sniffer.mock.trace.TraceSegmentBuilder;
import org.skywalking.apm.sniffer.mock.trace.builders.span.DubboSpanGenerator;
import org.skywalking.apm.sniffer.mock.trace.builders.span.TomcatSpanGenerator;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

/**
 * A Trace segment contains two spans with ChildOf relations,
 * the parent is a Tomcat span,
 * the child is a Dubbo client span.
 *
 * @author wusheng
 */
public enum TomcatDubboClientTraceBuilder implements TraceSegmentBuilder {
    INSTANCE;

    @Override
    public TraceSegment build(MockTracingContextListener listener) {
        TomcatSpanGenerator.ON200 rootSpan = new TomcatSpanGenerator.ON200();
        rootSpan.build(new DubboSpanGenerator.Client());
        rootSpan.generate();
        return listener.getFinished(0);
    }
}
