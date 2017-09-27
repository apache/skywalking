package org.skywalking.apm.toolkit.opentracing;

import io.opentracing.ActiveSpan;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

/**
 * @author wusheng
 */
public class SkywalkingTracer implements Tracer {

    public SpanBuilder buildSpan(String operationName) {
        return new SkywalkingSpanBuilder(operationName);
    }

    @NeedSnifferActivation
    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {

    }

    @NeedSnifferActivation
    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        return new TextMapContext();
    }

    @Override
    public ActiveSpan activeSpan() {
        return new SkywalkingActiveSpan(new SkywalkingSpan(this));
    }

    @Override
    public ActiveSpan makeActive(Span span) {
        if (span instanceof SkywalkingSpan) {
            return new SkywalkingActiveSpan((SkywalkingSpan)span);
        } else {
            throw new IllegalArgumentException("span must be a type of SkywalkingSpan");
        }
    }
}
