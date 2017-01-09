package com.a.eye.skywalking.api;

import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Span;
import com.a.eye.skywalking.network.grpc.TraceId;

import static com.a.eye.skywalking.util.TraceIdUtil.formatTraceId;

public class Tracing {
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
}
