package org.skywalking.apm.toolkit.opentracing;

import io.opentracing.SpanContext;
import java.util.Map;

/**
 * Skywalking tracer context based on {@link ThreadLocal} auto mechanism.
 *
 * @author wusheng
 */
public class SkywalkingContext implements SpanContext {
    public static final SkywalkingContext INSTANCE = new SkywalkingContext();

    private SkywalkingContext() {
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return null;
    }
}
