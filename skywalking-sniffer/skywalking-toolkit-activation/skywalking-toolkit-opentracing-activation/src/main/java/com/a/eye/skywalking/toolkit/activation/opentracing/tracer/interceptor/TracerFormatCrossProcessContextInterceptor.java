package com.a.eye.skywalking.toolkit.activation.opentracing.tracer.interceptor;

import com.a.eye.skywalking.api.Tracing;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Span;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * @author zhangxin
 */
public class TracerFormatCrossProcessContextInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        Span span = Tracing.getCurrentSpan();
        if (span != null) {
           return new ContextData(span.getTraceId(), generateSubParentLevelId(span), span.getRouteKey()).toString();
        }
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {

    }

    private String generateSubParentLevelId(Span spanData) {
        if (spanData.getParentLevel() == null || spanData.getParentLevel().length() == 0) {
            return spanData.getLevelId() + "";
        }

        return spanData.getParentLevel() + "." + spanData.getLevelId();
    }
}
