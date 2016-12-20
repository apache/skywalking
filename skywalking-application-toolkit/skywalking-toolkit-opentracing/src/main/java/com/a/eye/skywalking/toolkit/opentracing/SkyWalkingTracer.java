package com.a.eye.skywalking.toolkit.opentracing;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * Created by wusheng on 2016/12/20.
 */
public class SkyWalkingTracer implements Tracer{
    public static Tracer INSTANCE = new SkyWalkingTracer();

    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new SkyWalkingSpanBuilder(operationName);
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C c) {

    }

    @Override
    public <C> SpanContext extract(Format<C> format, C c) {
        return null;
    }
}
