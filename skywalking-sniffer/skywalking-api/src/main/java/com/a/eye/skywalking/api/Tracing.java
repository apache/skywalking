package com.a.eye.skywalking.api;

import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Span;
import com.a.eye.skywalking.network.grpc.TraceId;

import static com.a.eye.skywalking.util.TraceIdUtil.formatTraceId;

public class Tracing {
    /**
     * 获取当前上下文中的TraceId
     *
     * @return
     */
    public static String getTraceId() {
        Span spanData = CurrentThreadSpanStack.peek();
        if (spanData == null) {
            return "";
        }

        return formatTraceId(spanData.getTraceId());
    }

    public static String getTracelevelId() {
        Span spanData = CurrentThreadSpanStack.peek();
        if (spanData == null) {
            return "";
        }

        return (spanData.getParentLevel() == null || spanData.getParentLevel().length() == 0) ?
                Integer.toString(spanData.getLevelId()) :
                spanData.getParentLevel() + "." + spanData.getLevelId();
    }

    public static String generateNextContextData() {
        Span spanData = CurrentThreadSpanStack.peek();
        if (spanData == null) {
            return null;
        }

        ContextData contextData = new ContextData(spanData);
        return contextData.toString();
    }
}
