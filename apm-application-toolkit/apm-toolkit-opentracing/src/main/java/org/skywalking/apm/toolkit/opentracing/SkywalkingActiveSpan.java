package org.skywalking.apm.toolkit.opentracing;

import io.opentracing.ActiveSpan;
import io.opentracing.SpanContext;
import java.util.Map;

/**
 * The <code>SkywalkingActiveSpan</code> is an extension of {@link SkywalkingSpan},
 * but because of Java inheritance restrict, only can do with a facade mode.
 *
 * @author wusheng
 */
public class SkywalkingActiveSpan implements ActiveSpan {
    private SkywalkingSpan span;

    public SkywalkingActiveSpan(SkywalkingSpan span) {
        this.span = span;
    }

    @Override
    public void deactivate() {
        span.finish();
    }

    @Override
    public void close() {
        this.deactivate();
    }

    @Override
    public Continuation capture() {
        return new SkywalkingContinuation();
    }

    @Override
    public SpanContext context() {
        return span.context();
    }

    @Override
    public ActiveSpan setTag(String key, String value) {
        span.setTag(key, value);
        return this;
    }

    @Override
    public ActiveSpan setTag(String key, boolean value) {
        span.setTag(key, value);
        return this;
    }

    @Override
    public ActiveSpan setTag(String key, Number value) {
        span.setTag(key, value);
        return this;
    }

    @Override
    public ActiveSpan log(Map<String, ?> fields) {
        span.log(fields);
        return this;
    }

    @Override
    public ActiveSpan log(long timestampMicroseconds, Map<String, ?> fields) {
        span.log(timestampMicroseconds, fields);
        return this;
    }

    @Override
    public ActiveSpan log(String event) {
        span.log(event);
        return this;
    }

    @Override
    public ActiveSpan log(long timestampMicroseconds, String event) {
        span.log(timestampMicroseconds, event);
        return this;
    }

    /**
     * Don't support baggage item.
     */
    @Override
    public ActiveSpan setBaggageItem(String key, String value) {
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

    @Override
    public ActiveSpan setOperationName(String operationName) {
        span.setOperationName(operationName);
        return this;
    }

    /**
     * Don't support logging with payload.
     */
    @Deprecated
    @Override
    public ActiveSpan log(String eventName, Object payload) {
        return this;
    }

    /**
     * Don't support logging with payload.
     */
    @Deprecated
    @Override
    public ActiveSpan log(long timestampMicroseconds, String eventName, Object payload) {
        return this;
    }
}
