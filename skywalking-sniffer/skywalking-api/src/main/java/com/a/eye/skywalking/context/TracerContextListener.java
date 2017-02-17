package com.a.eye.skywalking.context;

import com.a.eye.skywalking.trace.TraceSegment;

/**
 * {@link TracerContextListener} is a status change listener of {@link TracerContext}.
 * Add a {@link TracerContextListener} implementation through {@link TracerContext}
 *
 * All this class's methods will be called concurrently. Make sure all implementations are thread-safe.
 *
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
