package com.a.eye.skywalking.api;

import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.model.RefContext;
import com.a.eye.skywalking.model.Span;

import static com.a.eye.skywalking.util.TraceIdUtil.formatTraceId;

public final class Tracing {
    /**
     * Get the traceId of current trace context.
     *
     * @return traceId, if it exists, or empty {@link String}.
     */
    public static String getTraceId() {
        Span spanData = CurrentThreadSpanStack.peek();
        if (spanData == null) {
            return "";
        }

        return formatTraceId(spanData.getTraceId());
    }


    /**
     * Get the current span of current trace.
     *
     * @return span. if it exists, or null
     */
    public static Span getCurrentSpan() {
        Span spanData = CurrentThreadSpanStack.peek();
        return spanData;
    }

    /**
     * the span will be tagged with the given key and value pair
     *
     * @param span
     * @param tagKey key of tag
     * @param tagValue value of tag
     */
    public static void tag(Span span, String tagKey, String tagValue) {
        span.tag(tagKey, tagValue);
    }

    /**
     * init the ref context
     *
     * @param refContext ref context
     */
    public static void initRefContext(RefContext refContext){
        CurrentThreadSpanStack.initRefContext(refContext);
    }
}
