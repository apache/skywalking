package org.skywalking.apm.sniffer.mock.trace;

import org.skywalking.apm.agent.core.context.TracingContext;
import org.skywalking.apm.sniffer.mock.context.MockTracerContextListener;
import org.skywalking.apm.sniffer.mock.trace.builders.trace.*;
import org.skywalking.apm.trace.TraceSegment;

/**
 * The <code>TraceSegmentBuilderFactory</code> contains all {@link TraceSegmentBuilder} implementations. All the
 * implementations can build a true {@link TraceSegment} object, and contain all necessary spans, with all tags/events,
 * all refs.
 * <p>
 * Created by wusheng on 2017/2/20.
 */
public enum TraceSegmentBuilderFactory {
    INSTANCE;

    /**
     * @see {@link SingleTomcat200TraceBuilder}
     */
    public TraceSegment singleTomcat200Trace() {
        return this.build(SingleTomcat200TraceBuilder.INSTANCE);
    }

    /**
     * @see {@link SingleTomcat404TraceBuilder}
     */
    public TraceSegment singleTomcat404Trace() {
        return this.build(SingleTomcat404TraceBuilder.INSTANCE);
    }

    /**
     * @see {@link SingleTomcat500TraceBuilder}
     */
    public TraceSegment singleTomcat500Trace() {
        return this.build(SingleTomcat500TraceBuilder.INSTANCE);
    }

    /**
     * @see {@link TomcatDubboClientTraceBuilder}
     */
    public TraceSegment traceOf_Tomcat_DubboClient() {
        return this.build(TomcatDubboClientTraceBuilder.INSTANCE);
    }

    /**
     * @see {@link DubboServerMysqlTraceBuilder}
     */
    public TraceSegment traceOf_DubboServer_MySQL() {
        return this.build(DubboServerMysqlTraceBuilder.INSTANCE);
    }

    private TraceSegment build(TraceSegmentBuilder builder) {
        MockTracerContextListener listener = new MockTracerContextListener();
        try {
            TracingContext.ListenerManager.add(listener);
            return builder.build(listener);
        } finally {
            TracingContext.ListenerManager.remove(listener);
        }
    }

}
