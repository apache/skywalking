package com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.toolkit.opentracing.SkyWalkingSpan;

/**
 * Intercept these following methods:
 * {@link SkyWalkingSpan#setTag(String, boolean)}
 * {@link SkyWalkingSpan#setTag(String, Number)}
 * {@link SkyWalkingSpan#setTag(String, String)}
 */
public class SpanSetTagInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        MethodInterceptResult result) {
        String key = (String)interceptorContext.allArguments()[0];
        Object value = interceptorContext.allArguments()[1];
        if (value instanceof String)
            ContextManager.activeSpan().setTag(key, (String)value);
        else if (value instanceof Boolean)
            ContextManager.activeSpan().setTag(key, (Boolean)value);
        else if (value instanceof Number)
            ContextManager.activeSpan().setTag(key, (Number)value);
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
        InstanceMethodInvokeContext interceptorContext) {

    }
}
