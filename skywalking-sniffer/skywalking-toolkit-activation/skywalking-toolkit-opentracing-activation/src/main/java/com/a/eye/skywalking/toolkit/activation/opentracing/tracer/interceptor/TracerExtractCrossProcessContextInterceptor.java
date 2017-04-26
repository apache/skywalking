package com.a.eye.skywalking.toolkit.activation.opentracing.tracer.interceptor;

import com.a.eye.skywalking.api.context.ContextCarrier;
import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.toolkit.opentracing.SkyWalkingTracer;

/**
 * Intercept {@link SkyWalkingTracer#formatExtractCrossProcessPropagationContextData(String)}
 */
public class TracerExtractCrossProcessContextInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        Object ret) {
        String contextDataStr = (String)interceptorContext.allArguments()[0];

        ContextCarrier carrier = new ContextCarrier();
        carrier.deserialize(contextDataStr);

        ContextManager.extract(carrier);
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
        InstanceMethodInvokeContext interceptorContext) {

    }
}
