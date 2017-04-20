package com.a.eye.skywalking.toolkit.opentracing;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import java.util.Collections;
import java.util.HashMap;
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

    private long startTime = 0L;

    private final Map<String, String> tags;

    private SpanContext parentContext;

    SkyWalkingSpanBuilder(String operationName) {
        this.operationName = operationName;
        this.tags = new HashMap<String, String>();
    }

    /**
     * In SkyWalkingTracer, SpanContext will not be used. Tracer will build reference by itself.
     *
     * @param spanContext
     * @return
     */
    @Override
    public Tracer.SpanBuilder asChildOf(SpanContext spanContext) {
        this.parentContext = spanContext;
        return this;
    }

    /**
     * In SkyWalkingTracer, Parent Span will not be used. Tracer will build reference by itself.
     *
     * @param span
     * @return
     */
    @Override
    public Tracer.SpanBuilder asChildOf(Span span) {
        asChildOf(span.context());
        return this;
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
        if (key != null && value != null) {
            tags.put(key, value);
        }
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, boolean value) {
        if (key != null) {
            tags.put(key, Boolean.toString(value));
        }
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, Number value) {
        if (key != null && value != null) {
            tags.put(key, value.toString());
        }
        return this;
    }

    @Override
    public Tracer.SpanBuilder withStartTimestamp(long startTime) {
        this.startTime = startTime;
        return this;
    }

    @Override
    public Span start() {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        return new SkyWalkingSpan(this.operationName, this.startTime, this.tags);
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return parentContext == null
            ? Collections.<String, String>emptyMap().entrySet()
            : parentContext.baggageItems();
    }
}
