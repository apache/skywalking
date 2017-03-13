package com.a.eye.skywalking.sniffer.mock.trace.builders.trace;

import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.sniffer.mock.trace.TraceSegmentBuilder;
import com.a.eye.skywalking.sniffer.mock.trace.builders.span.DubboSpanGenerator;
import com.a.eye.skywalking.sniffer.mock.trace.builders.span.MySQLGenerator;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * @author wusheng
 */
public enum DubboServerMysqlTraceBuilder implements TraceSegmentBuilder {
    INSTANCE;

    @Override public TraceSegment build(MockTracerContextListener listener) {
        DubboSpanGenerator.Server rootSpan = new DubboSpanGenerator.Server();
        rootSpan.build(new MySQLGenerator.Query());
        rootSpan.generate();
        return listener.getFinished(0);
    }
}
