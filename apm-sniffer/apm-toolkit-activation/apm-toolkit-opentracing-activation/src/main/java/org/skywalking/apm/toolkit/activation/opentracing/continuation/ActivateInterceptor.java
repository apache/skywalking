package org.skywalking.apm.toolkit.activation.opentracing.continuation;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.ContextSnapshot;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

public class ActivateInterceptor implements InstanceMethodsAroundInterceptor {
    @Override public void beforeMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
    }

    @Override public Object afterMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        Object contextSnapshot = objInst.getSkyWalkingDynamicField();
        if (contextSnapshot != null) {
            ContextManager.continued((ContextSnapshot)contextSnapshot);
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
