package org.skywalking.apm.toolkit.activation.opentracing.span;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

public class SpanFinishInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        AbstractSpan abstractSpan = (AbstractSpan)objInst.getSkyWalkingDynamicField();
        if (abstractSpan != null) {
            ContextManager.stopSpan(abstractSpan);
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
