package com.a.eye.skywalking.toolkit.opentracing;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import java.util.Map;

/**
 * All source code in SkyWalkingSpanBuilder acts like an NoopSpanBuilder.
 * Actually, it is NOT.
 * The whole logic will be added after toolkit-activation.
 *
 * Created by wusheng on 2016/12/20.
 */
public class SkyWalkingSpanBuilder implements Tracer.SpanBuilder {
    private String operationName;

    SkyWalkingSpanBuilder(String operationName){
        this.operationName = operationName;
    }

    @Override
    public Tracer.SpanBuilder asChildOf(SpanContext spanContext) {
        return null;
    }

    @Override
    public Tracer.SpanBuilder asChildOf(Span span) {
        return null;
    }

    @Override
    public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        if (referenceType.equals(References.CHILD_OF)) {
            return asChildOf(referencedContext);
        } else {
            return this;
        }
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, String value) {
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, boolean value) {
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, Number value) {
        return this;
    }

    @Override
    public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
        return this;
    }

    @Override
    public Span start() {
        return new SkyWalkingSpan();
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return null;
    }
}
