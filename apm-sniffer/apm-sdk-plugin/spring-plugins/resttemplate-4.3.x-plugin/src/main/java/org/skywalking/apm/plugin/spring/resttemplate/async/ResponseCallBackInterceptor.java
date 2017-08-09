package org.skywalking.apm.plugin.spring.resttemplate.async;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

public class ResponseCallBackInterceptor implements InstanceMethodsAroundInterceptor {

    @Override public void beforeMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        EnhancedInstance successCallBak = (EnhancedInstance)allArguments[0];
        successCallBak.setSkyWalkingDynamicField(objInst.getSkyWalkingDynamicField());

        if (allArguments.length == 2) {
            EnhancedInstance failedCallBack = (EnhancedInstance)allArguments[1];
            failedCallBack.setSkyWalkingDynamicField(objInst.getSkyWalkingDynamicField());
        }
    }

    @Override public Object afterMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
