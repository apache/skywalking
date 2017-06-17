package org.skywalking.apm.sniffer.mock.trace.builders.trace;

import org.skywalking.apm.sniffer.mock.context.MockTracerContextListener;
import org.skywalking.apm.sniffer.mock.trace.TraceSegmentBuilder;
import org.skywalking.apm.sniffer.mock.trace.builders.span.TomcatSpanGenerator;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

/**
 * A Trace contains only one span, which represent a tomcat server side span.
 * <p>
 * Created by wusheng on 2017/2/20.
 */
public enum SingleTomcat404TraceBuilder implements TraceSegmentBuilder {
    INSTANCE;

    @Override
    public TraceSegment build(MockTracerContextListener listener) {
        TomcatSpanGenerator.ON404.INSTANCE.generate();
        return listener.getFinished(0);
    }
}
