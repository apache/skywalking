package com.ai.cloud.skywalking.plugin.interceptor.enhance;

public class StaticMethodInvokeContext extends MethodInvokeContext {
    /**
     * 代理类名
     */
    private Class clazz;

    StaticMethodInvokeContext(Class clazz, String methodName, Object[] allArguments, Class<?>[] parameterTypes) {
        super(methodName, allArguments, parameterTypes);
        this.clazz = clazz;
    }

    public Class claszz() {
        return clazz;
    }
}
