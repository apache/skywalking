package com.a.eye.skywalking.context;

import com.a.eye.skywalking.queue.TraceSegmentProcessQueue;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * {@link TracerContext} controls the whole context of {@link TraceSegment}. Any {@link TraceSegment} relates to
 * single-thread, so this context use {@link ThreadLocal} to maintain the context, and make sure, since a {@link
 * TraceSegment} starts, all ChildOf spans are in the same context.
 *
 * What is 'ChildOf'? {@see https://github.com/opentracing/specification/blob/master/specification.md#references-between-spans}
 *
 * Also, {@link ContextManager} delegates to all {@link TracerContext}'s major methods: {@link
 * TracerContext#createSpan(String)}, {@link TracerContext#activeSpan()}, {@link TracerContext#stopSpan(Span)}
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

    private TracerContext get() {
        TracerContext segment = CONTEXT.get();
        if (segment == null) {
            segment = new TracerContext();
            CONTEXT.set(segment);
        }
        return segment;
    }

    public Span createSpan(String operationName) {
        return get().createSpan(operationName);
    }

    public Span activeSpan() {
        return get().activeSpan();
    }

    public void stopSpan(Span span) {
        get().stopSpan(span);
    }

    public void stopSpan() {
        stopSpan(activeSpan());
    }
}
