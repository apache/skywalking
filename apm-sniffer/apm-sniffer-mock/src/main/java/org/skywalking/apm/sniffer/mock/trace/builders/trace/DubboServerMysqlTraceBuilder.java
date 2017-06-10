package org.skywalking.apm.sniffer.mock.trace.builders.trace;

import org.skywalking.apm.sniffer.mock.context.MockTracerContextListener;
import org.skywalking.apm.sniffer.mock.trace.TraceSegmentBuilder;
import org.skywalking.apm.sniffer.mock.trace.builders.span.DubboSpanGenerator;
import org.skywalking.apm.sniffer.mock.trace.builders.span.MySQLGenerator;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

/**
 * @author wusheng
 */
public enum DubboServerMysqlTraceBuilder implements TraceSegmentBuilder {
    INSTANCE;

    @Override
    public TraceSegment build(MockTracerContextListener listener) {
        DubboSpanGenerator.Server rootSpan = new DubboSpanGenerator.Server();
        rootSpan.build(new MySQLGenerator.Query());
        rootSpan.generate();
        return listener.getFinished(0);
    }
}
