package com.a.eye.skywalking.toolkit.opentracing;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

import java.nio.ByteBuffer;

/**
 * All source code in SkyWalkingTracer acts like an NoopTracer.
 * Actually, it is NOT.
 * The whole logic will be added after toolkit-activation.
 * <p>
 * Created by wusheng on 2016/12/20.
 */
public class SkyWalkingTracer implements Tracer {
    private static String TRACE_HEAD_NAME = "SkyWalking-TRACING-NAME";

    public static Tracer INSTANCE = new SkyWalkingTracer();

    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new SkyWalkingSpanBuilder(operationName);
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        if (Format.Builtin.TEXT_MAP.equals(format) || Format.Builtin.HTTP_HEADERS.equals(format)) {
            ((TextMap) carrier).put(TRACE_HEAD_NAME, formatCrossProcessPropagationContextData());
        } else if (Format.Builtin.BINARY.equals(format)) {
            byte[] key = TRACE_HEAD_NAME.getBytes(ByteBufferContext.CHARSET);
            byte[] value = formatCrossProcessPropagationContextData().getBytes(ByteBufferContext.CHARSET);
            ((ByteBuffer) carrier).put(ByteBufferContext.ENTRY);
            ((ByteBuffer) carrier).putInt(key.length);
            ((ByteBuffer) carrier).putInt(value.length);
            ((ByteBuffer) carrier).put(key);
            ((ByteBuffer) carrier).put(value);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        if (Format.Builtin.TEXT_MAP.equals(format) || Format.Builtin.HTTP_HEADERS.equals(format)) {
            TextMap textMapCarrier = (TextMap) carrier;
            extractCrossProcessPropagationContextData(textMapCarrier);
            return new TextMapContext(textMapCarrier);
        } else if (Format.Builtin.BINARY.equals(format)) {
            ByteBuffer byteBufferCarrier = (ByteBuffer)carrier;
            extractCrossProcessPropagationContextData(byteBufferCarrier);
            return new ByteBufferContext((ByteBuffer)carrier);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }


    /**
     * set context data in toolkit-opentracing-activation
     *
     * @return
     */
    private String formatCrossProcessPropagationContextData() {
        return "";
    }

    /**
     * read context data in toolkit-opentracing-activation
     *
     * @param textMapCarrier
     */
    private void extractCrossProcessPropagationContextData(TextMap textMapCarrier) {

    }

    /**
     * read context data in toolkit-opentracing-activation
     *
     * @param byteBufferCarrier
     */
    private void extractCrossProcessPropagationContextData(ByteBuffer byteBufferCarrier) {

    }
}
