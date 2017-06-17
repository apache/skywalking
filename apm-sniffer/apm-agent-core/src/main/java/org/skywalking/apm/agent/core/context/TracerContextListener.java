package org.skywalking.apm.agent.core.context;

import org.skywalking.apm.agent.core.context.trace.TraceSegment;

/**
 * {@link TracerContextListener} is a status change listener of {@link TracerContext}.
 * Add a {@link TracerContextListener} implementation through {@link TracerContext}
 * <p>
 * All this class's methods will be called concurrently. Make sure all implementations are thread-safe.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public interface TracerContextListener {
    /**
     * This method will be called, after the {@link TracerContext#finish()}
     *
     * @param traceSegment finished {@link TraceSegment}
     */
    void afterFinished(TraceSegment traceSegment);
}
