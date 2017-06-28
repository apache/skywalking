package org.skywalking.apm.toolkit.activation.opentracing.span.interceptor;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.toolkit.opentracing.SkyWalkingSpan;

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
            ContextManager.stopSpan((Long) allArguments[0]);
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
