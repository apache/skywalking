package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.trace.TraceSegment;

/**
 * {@link TracerContextListener} is a status change listener of {@link TracingContext}.
 * Add a {@link TracerContextListener} implementation through {@link TracingContext}
 * <p>
 * All this class's methods will be called concurrently. Make sure all implementations are thread-safe.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public interface TracerContextListener {
    /**
     * This method will be called, after the {@link TracingContext#finish()}
     *
     * @param traceSegment finished {@link TraceSegment}
     */
    void afterFinished(TraceSegment traceSegment);
}
