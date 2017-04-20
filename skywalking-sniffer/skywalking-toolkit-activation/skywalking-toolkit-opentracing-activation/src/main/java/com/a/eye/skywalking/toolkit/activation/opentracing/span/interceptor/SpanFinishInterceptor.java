package com.a.eye.skywalking.toolkit.activation.opentracing.span.interceptor;

import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.toolkit.opentracing.SkyWalkingSpan;

/**
 * Intercept these following methods:
 * {@link SkyWalkingSpan#finish()}
 * {@link SkyWalkingSpan#finish(long)}
 */
public class SpanFinishInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        MethodInterceptResult result) {
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        Object ret) {
        Object[] allArguments = interceptorContext.allArguments();

        if (allArguments.length == 1) {
            ContextManager.stopSpan((Long)allArguments[0]);
        } else {
            ContextManager.stopSpan();
        }

        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
        InstanceMethodInvokeContext interceptorContext) {
    }
}
