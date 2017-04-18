package com.a.eye.skywalking.toolkit.opentracing;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.TextMap;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

/**
 * Created by wusheng on 2016/12/21.
 */
public class SkyWalkingTracerTest {
    @Test
    public void testBuildSpan() {
        Tracer tracer = SkyWalkingTracer.INSTANCE;
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan("/http/serviceName");

        SpanContext context = new TextMapContext(new TextMap() {

            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                throw new UnsupportedOperationException(
                    "TextMapInjectAdapter should only be used with Tracer.inject()");
            }

            @Override
            public void put(String key, String value) {

            }
        });
        spanBuilder.asChildOf(context).withTag("example.tag", "testtag");
        Span span = spanBuilder.start();

        span.finish();
    }
}
