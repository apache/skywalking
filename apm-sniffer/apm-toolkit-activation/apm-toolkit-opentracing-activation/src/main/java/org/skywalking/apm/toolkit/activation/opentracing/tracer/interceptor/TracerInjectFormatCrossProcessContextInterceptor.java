package org.skywalking.apm.toolkit.activation.opentracing.tracer.interceptor;

import org.skywalking.apm.api.context.ContextCarrier;
import org.skywalking.apm.api.context.ContextManager;
import org.skywalking.apm.api.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.api.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.toolkit.opentracing.SkyWalkingTracer;

/**
 * Intercept {@link SkyWalkingTracer#formatInjectCrossProcessPropagationContextData()}
 */
public class TracerInjectFormatCrossProcessContextInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                              Object ret) {
        ContextCarrier carrier = new ContextCarrier();
        ContextManager.inject(carrier);
        return carrier.serialize();
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
                                      InstanceMethodInvokeContext interceptorContext) {

    }
}
