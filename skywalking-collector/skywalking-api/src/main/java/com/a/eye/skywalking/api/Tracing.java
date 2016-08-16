package com.a.eye.skywalking.api;

import com.a.eye.skywalking.conf.AuthDesc;
import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.protocol.Span;

public class Tracing {
    /**
     * 获取当前上下文中的TraceId
     *
     * @return
     */
    public static String getTraceId() {
        if (!AuthDesc.isAuth())
            return "";

        Span spanData = CurrentThreadSpanStack.peek();
        if (spanData == null) {
            return "";
        }

        return spanData.getTraceId();
    }

    public static String getTracelevelId() {
        if (!AuthDesc.isAuth())
            return "";

        Span spanData = CurrentThreadSpanStack.peek();
        if (spanData == null) {
            return "";
        }

        return (spanData.getParentLevel() == null || spanData.getParentLevel().length() == 0) ?
                Integer.toString(spanData.getLevelId()) :
                spanData.getParentLevel() + "." + spanData.getLevelId();
    }

    public static String generateNextContextData() {
        if (!AuthDesc.isAuth())
            return null;

        Span spanData = CurrentThreadSpanStack.peek();
        if (spanData == null) {
            return null;
        }

        ContextData contextData = new ContextData(spanData);
        return contextData.toString();
    }
}
