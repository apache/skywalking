package org.skywalking.apm.toolkit.opentracing;

import io.opentracing.ActiveSpan;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;

/**
 * @author wusheng
 */
public class SkywalkingTracer implements Tracer {
    private static String TRACE_HEAD_NAME = "sw3";

    @NeedSnifferActivation("1. ContextManager#inject" +
        "2. ContextCarrier#serialize")
    private String inject() {
        return null;
    }

    @NeedSnifferActivation("1. ContextCarrier#deserialize" +
        "2. ContextManager#extract")
    private void extract(String carrier) {

    }

    @Override
    public SpanBuilder buildSpan(String operationName) {
        return new SkywalkingSpanBuilder(operationName);
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        if (Format.Builtin.TEXT_MAP.equals(format) || Format.Builtin.HTTP_HEADERS.equals(format)) {
            ((TextMap)carrier).put(TRACE_HEAD_NAME, inject());
        } else if (Format.Builtin.BINARY.equals(format)) {
            byte[] key = TRACE_HEAD_NAME.getBytes(ByteBufferContext.CHARSET);
            byte[] value = inject().getBytes(ByteBufferContext.CHARSET);
            ((ByteBuffer)carrier).put(key);
            ((ByteBuffer)carrier).putInt(value.length);
            ((ByteBuffer)carrier).put(value);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }

    }

    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        if (Format.Builtin.TEXT_MAP.equals(format) || Format.Builtin.HTTP_HEADERS.equals(format)) {
            TextMap textMapCarrier = (TextMap)carrier;
            extract(fetchContextData(textMapCarrier));
            return new TextMapContext(textMapCarrier);
        } else if (Format.Builtin.BINARY.equals(format)) {
            ByteBuffer byteBufferCarrier = (ByteBuffer)carrier;
            extract(fetchContextData(byteBufferCarrier));
            return new ByteBufferContext((ByteBuffer)carrier);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }
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
