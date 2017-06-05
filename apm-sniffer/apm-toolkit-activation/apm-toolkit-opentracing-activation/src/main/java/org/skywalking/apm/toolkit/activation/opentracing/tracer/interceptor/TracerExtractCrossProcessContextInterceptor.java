package org.skywalking.apm.toolkit.activation.opentracing.tracer.interceptor;

import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.toolkit.opentracing.SkyWalkingTracer;

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
        String contextDataStr = (String) interceptorContext.allArguments()[0];

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
