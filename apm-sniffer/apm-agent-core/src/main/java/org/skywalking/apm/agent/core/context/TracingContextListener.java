package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.agent.core.context.trace.TraceSegment;

public interface TracingContextListener {
    void afterFinished(TraceSegment traceSegment);
}
