package com.a.eye.skywalking.context;

import com.a.eye.skywalking.queue.TraceSegmentProcessQueue;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * {@link TracerContext} controls the whole context of {@link TraceSegment}. Any {@link TraceSegment} relates to
 * single-thread, so this context use {@link ThreadLocal} to maintain the context, and make sure, since a {@link
 * TraceSegment} starts, all ChildOf spans are in the same context.
 *
 * What is 'ChildOf'? {@see https://github.com/opentracing/specification/blob/master/specification.md#references-between-spans}
 *
 *
 * Created by wusheng on 2017/2/17.
 */
public enum ContextManager implements TracerContextListener {
    INSTANCE {
        @Override public void afterFinished(TraceSegment traceSegment) {
            CONTEXT.remove();
        }
    };

    ContextManager() {
        TracerContext.ListenerManager.add(this);
        TraceSegmentProcessQueue.INSTANCE.start();
    }

    private static ThreadLocal<TracerContext> CONTEXT = new ThreadLocal<>();

    public TracerContext get() {
        TracerContext segment = CONTEXT.get();
        if (segment == null) {
            segment = new TracerContext();
            CONTEXT.set(segment);
        }
        return segment;
    }
}
