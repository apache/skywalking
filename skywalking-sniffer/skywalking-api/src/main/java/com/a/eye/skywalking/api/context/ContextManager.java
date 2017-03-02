package com.a.eye.skywalking.api.context;

import com.a.eye.skywalking.api.queue.TraceSegmentProcessQueue;
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

    /**
     * @see {@link TracerContext#inject(ContextCarrier)}
     */
    public void inject(ContextCarrier carrier) {
        get().inject(carrier);
    }

    /**
     *@see {@link TracerContext#extract(ContextCarrier)}
     */
    public void extract(ContextCarrier carrier) {
        get().extract(carrier);
    }

    /**
     * @return the {@link TraceSegment#traceSegmentId} if exist. Otherwise, "N/A".
     */
    public String getTraceSegmentId(){
        TracerContext segment = CONTEXT.get();
        if(segment == null){
            return "N/A";
        }else{
            return segment.getTraceSegmentId();
        }
    }

    public Span createSpan(String operationName) {
        return get().createSpan(operationName);
    }

    public Span createSpan(String operationName, long startTime) {
        return get().createSpan(operationName);
    }

    public Span activeSpan() {
        return get().activeSpan();
    }

    public void stopSpan(Span span) {
        get().stopSpan(span);
    }

    public void stopSpan(Long endTime) {
        get().stopSpan(activeSpan(), endTime);
    }

    public void stopSpan() {
        stopSpan(activeSpan());
    }
}
