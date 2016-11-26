package com.a.eye.skywalking.api;

import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Span;
import com.a.eye.skywalking.network.grpc.TraceId;

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

    public static String formatTraceId(TraceId traceId){
        StringBuilder traceIdBuilder = new StringBuilder();
        for (Long segment : traceId.getSegmentsList()) {
            traceIdBuilder.append(segment).append(".");
        }

        return traceIdBuilder.substring(0, traceIdBuilder.length() - 1).toString();
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
