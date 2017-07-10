package org.skywalking.apm.toolkit.activation.opentracing.tracer;

import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

public class SkywalkingTracerExtractInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        String carrier = (String)allArguments[0];
        ContextCarrier contextCarrier = new ContextCarrier().deserialize(carrier);
        ContextManager.extract(contextCarrier);
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
