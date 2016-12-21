package com.a.eye.skywalking.toolkit.opentracing;

import io.opentracing.SpanContext;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by wusheng on 2016/12/21.
 */
public class ByteBufferContext implements SpanContext {
    static final Charset CHARSET = Charset.forName("UTF-8");

    static final byte NO_ENTRY = 0;
    static final byte ENTRY = 1;

    private final ByteBuffer byteBuffer;

    ByteBufferContext(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return new HashMap<String, String>().entrySet();
    }
}
