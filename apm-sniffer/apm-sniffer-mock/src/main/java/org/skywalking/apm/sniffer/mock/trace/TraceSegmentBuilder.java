package org.skywalking.apm.sniffer.mock.trace;

import org.skywalking.apm.sniffer.mock.context.MockTracingContextListener;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

/**
 * Created by wusheng on 2017/2/20.
 */
public interface TraceSegmentBuilder {
    TraceSegment build(MockTracingContextListener listener);
}
