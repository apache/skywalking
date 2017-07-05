package org.skywalking.apm.agent.core.plugin;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

public class MockPluginInterceptor implements InstanceMethodsAroundInterceptor, StaticMethodsAroundInterceptor, InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(allArguments[0]);
    }

    @Override
    public void beforeMethod(Class clazz, String methodName, Object[] allArguments, Class<?>[] parameterTypes,
        MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(Class clazz, String methodName, Object[] allArguments, Class<?>[] parameterTypes,
        Object ret) {
        return ret + "_STATIC";
    }

    @Override
    public void handleMethodException(Class clazz, String methodName, Object[] allArguments, Class<?>[] parameterTypes,
        Throwable t) {

    }

    @Override public void beforeMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override public Object afterMethod(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret + String.valueOf(objInst.getSkyWalkingDynamicField());
    }

    @Override public void handleMethodException(EnhancedInstance objInst, String methodName, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
