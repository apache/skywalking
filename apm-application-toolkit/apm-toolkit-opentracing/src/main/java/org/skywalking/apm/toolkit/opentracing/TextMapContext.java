package org.skywalking.apm.toolkit.opentracing;

import io.opentracing.SpanContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by wusheng on 2016/12/21.
 */
public class TextMapContext implements SpanContext {
    public TextMapContext() {
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return new HashMap<String, String>(0).entrySet();
    }
}
