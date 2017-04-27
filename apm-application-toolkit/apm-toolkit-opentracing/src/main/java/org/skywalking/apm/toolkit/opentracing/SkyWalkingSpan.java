package org.skywalking.apm.toolkit.opentracing;

import io.opentracing.Span;
import io.opentracing.SpanContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wusheng on 2016/12/20.
 */
public class SkyWalkingSpan implements Span, SpanContext {
    private String operationName;

    private long startTime;

    private Map<String, String> tags;

    private final Map<String, String> baggageItems;

    SkyWalkingSpan(String operationName, long startTime, Map<String, String> tags) {
        this.operationName = operationName;
        this.startTime = startTime;
        this.tags = tags;
        baggageItems = new HashMap<String, String>();
    }

    @Override
    public SpanContext context() {
        return this;
    }

    @Override
    public void finish() {

    }

    @Override
    public void finish(long finishMicros) {

    }

    @Override
    public void close() {

    }

    @Override
    public Span setTag(String key, String value) {
        return this;
    }

    @Override
    public Span setTag(String key, boolean value) {
        return this;
    }

    @Override
    public Span setTag(String key, Number value) {
        return this;
    }

    @Override
    public Span log(Map<String, ?> fields) {
        return this;
    }

    @Override
    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        return this;
    }

    @Override
    public Span log(String event) {
        return this;
    }

    @Override
    public Span log(long timestampMicroseconds, String event) {
        return this;
    }

    @Override
    public Span setBaggageItem(String key, String value) {
        baggageItems.put(key, value);
        return this;
    }

    @Override
    public String getBaggageItem(String key) {
        return baggageItems.get(key);
    }

    @Override
    public Span setOperationName(String operationName) {
        return this;
    }

    @Override
    public Span log(String eventName, Object payload) {
        return this;
    }

    @Override
    public Span log(long timestampMicroseconds, String eventName, Object payload) {
        return this;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return baggageItems.entrySet();
    }
}
