package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

/**
 * A interceptor, which intercept method's invocation. The target methods will be defined in {@link
 * ClassEnhancePluginDefine}'s subclass, most likely in {@link ClassInstanceMethodsEnhancePluginDefine}
 *
 * @author wusheng
 */
public interface InstanceMethodsAroundInterceptor {
    /**
     * called before target method invocation.
     *
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    void beforeMethod(Object objInst, String methodName, Object[] allArguments, Class<?>[] argumentsTypes,
        FieldSetter dynamicFieldSetter,
        FieldGetter dynamicFieldGetter,
        MethodInterceptResult result) throws Throwable;

    /**
     * called after target method invocation. Even method's invocation triggers an exception.
     *
     * @param ret the method's original return value.
     * @return the method's actual return value.
     * @throws Throwable
     */
    Object afterMethod(Object objInst, String methodName, Object[] allArguments, Class<?>[] argumentsTypes,
        FieldSetter dynamicFieldSetter,
        FieldGetter dynamicFieldGetter,
        Object ret) throws Throwable;

    /**
     * called when occur exception.
     *
     * @param t the exception occur.
     */
    void handleMethodException(Object objInst, String methodName, Object[] allArguments, Class<?>[] argumentsTypes,
        FieldSetter dynamicFieldSetter,
        FieldGetter dynamicFieldGetter,
        Throwable t);
}
