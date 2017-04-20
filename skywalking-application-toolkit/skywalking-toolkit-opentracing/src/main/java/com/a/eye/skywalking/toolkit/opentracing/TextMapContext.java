package com.a.eye.skywalking.toolkit.opentracing;

import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wusheng on 2016/12/21.
 */
public class TextMapContext implements SpanContext {
    private final TextMap textMap;

    TextMapContext(TextMap textMap) {
        this.textMap = textMap;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return new HashMap<String, String>().entrySet();
    }
}
