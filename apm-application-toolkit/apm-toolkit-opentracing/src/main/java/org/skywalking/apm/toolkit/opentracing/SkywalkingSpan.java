package org.skywalking.apm.toolkit.opentracing;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import java.util.HashMap;
import java.util.Map;

/**
 * @author wusheng
 */
public class SkywalkingSpan implements Span {
    @NeedSnifferActivation(
        "1.ContextManager#createSpan (Entry,Exit,Local based on builder)." +
            "2.set the span reference to the dynamic field of enhanced SkywalkingSpan")
    public SkywalkingSpan(SkywalkingSpanBuilder builder) {
    }

    /**
     * Create a shell span for {@link SkywalkingTracer#activeSpan()}
     *
     * @param tracer
     */
    @NeedSnifferActivation(
        "1. set the span reference to the dynamic field of enhanced SkywalkingSpan")
    public SkywalkingSpan(SkywalkingTracer tracer) {

    }

    @NeedSnifferActivation("Override span's operationName, which has been given at ")
    @Override
    public Span setOperationName(String operationName) {
        return this;
    }

    @NeedSnifferActivation("AbstractTracingSpan#log(long timestampMicroseconds, Map<String, ?> fields)")
    @Override
    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        return this;
    }

    /**
     * Stop the active span
     *
     * @param finishMicros
     */
    @NeedSnifferActivation(
        "1.ContextManager#stopSpan(AbstractSpan span)" +
            "2. The parameter of stop methed is from the dynamic field of enhanced SkywalkingSpan")
    @Override
    public void finish(long finishMicros) {

    }

    @Override
    public Span log(long timestampMicroseconds, String event) {
        Map<String, String> eventMap = new HashMap<String, String>(1);
        eventMap.put("event", event);
        return log(timestampMicroseconds, eventMap);
    }

    @Override
    public void finish() {
        this.finish(System.currentTimeMillis());
    }

    @Override
    public SpanContext context() {
        return SkywalkingContext.INSTANCE;
    }

    @Override public Span setTag(String key, String value) {
        return null;
    }

    @Override public Span setTag(String key, boolean value) {
        return null;
    }

    @Override public Span setTag(String key, Number value) {
        return null;
    }

    @Override
    public Span log(Map<String, ?> fields) {
        return log(System.currentTimeMillis(), fields);
    }

    @Override
    public Span log(String event) {
        return log(System.currentTimeMillis(), event);
    }

    /**
     * Don't support baggage item.
     */
    @Override
    public Span setBaggageItem(String key, String value) {
        return this;
    }

    /**
     * Don't support baggage item.
     *
     * @return null, always.
     */
    @Override
    public String getBaggageItem(String key) {
        return null;
    }

    /**
     * Don't support logging with payload.
     */
    @Deprecated
    @Override
    public Span log(String eventName, Object payload) {
        return this;
    }

    /**
     * Don't support logging with payload.
     */
    @Deprecated
    @Override
    public Span log(long timestampMicroseconds, String eventName, Object payload) {
        return this;
    }
}
