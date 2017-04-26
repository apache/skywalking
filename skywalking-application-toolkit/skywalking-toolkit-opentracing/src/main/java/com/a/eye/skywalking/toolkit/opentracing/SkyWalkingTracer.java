package com.a.eye.skywalking.toolkit.opentracing;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;

/**
 * All source code in SkyWalkingTracer acts like an NoopTracer.
 * Actually, it is NOT.
 * The whole logic will be added after toolkit-activation.
 * <p>
 * Created by wusheng on 2016/12/20.
 */
public class SkyWalkingTracer implements Tracer {
    private static String TRACE_HEAD_NAME = "sw3";

    public static Tracer INSTANCE = new SkyWalkingTracer();


    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new SkyWalkingSpanBuilder(operationName);
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        if (Format.Builtin.TEXT_MAP.equals(format) || Format.Builtin.HTTP_HEADERS.equals(format)) {
            ((TextMap) carrier).put(TRACE_HEAD_NAME, formatInjectCrossProcessPropagationContextData());
        } else if (Format.Builtin.BINARY.equals(format)) {
            byte[] key = TRACE_HEAD_NAME.getBytes(ByteBufferContext.CHARSET);
            byte[] value = formatInjectCrossProcessPropagationContextData().getBytes(ByteBufferContext.CHARSET);
            ((ByteBuffer) carrier).put(key);
            ((ByteBuffer) carrier).putInt(value.length);
            ((ByteBuffer) carrier).put(value);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        if (Format.Builtin.TEXT_MAP.equals(format) || Format.Builtin.HTTP_HEADERS.equals(format)) {
            TextMap textMapCarrier = (TextMap) carrier;
            formatExtractCrossProcessPropagationContextData(fetchContextData(textMapCarrier));
            return new TextMapContext(textMapCarrier);
        } else if (Format.Builtin.BINARY.equals(format)) {
            ByteBuffer byteBufferCarrier = (ByteBuffer) carrier;
            formatExtractCrossProcessPropagationContextData(fetchContextData(byteBufferCarrier));
            return new ByteBufferContext((ByteBuffer) carrier);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * set context data in toolkit-opentracing-activation
     */
    private String formatInjectCrossProcessPropagationContextData() {
        return "";
    }

    /**
     * read context data in toolkit-opentracing-activation
     */
    private void formatExtractCrossProcessPropagationContextData(String contextData) {
    }

    private String fetchContextData(TextMap textMap) {
        Iterator<Map.Entry<String, String>> iterator = textMap.iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (TRACE_HEAD_NAME.equals(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private String fetchContextData(ByteBuffer byteBuffer) {
        String contextDataStr = new String(byteBuffer.array(), Charset.forName("UTF-8"));
        int index = contextDataStr.indexOf(TRACE_HEAD_NAME);
        if (index == -1) {
            return null;
        }

        try {
            byteBuffer.position(index + TRACE_HEAD_NAME.getBytes().length);
            byte[] contextDataBytes = new byte[byteBuffer.getInt()];
            byteBuffer.get(contextDataBytes);
            return new String(contextDataBytes, Charset.forName("UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }
}
