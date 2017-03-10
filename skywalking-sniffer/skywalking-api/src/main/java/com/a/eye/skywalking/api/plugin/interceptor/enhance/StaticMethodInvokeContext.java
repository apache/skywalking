package com.a.eye.skywalking.api.plugin.interceptor.enhance;

/**
 * Static method invoke context.
 * Beside all in {@link MethodInvokeContext}, plus the class type.
 *
 * @author wusheng
 */
public class StaticMethodInvokeContext extends MethodInvokeContext {
    /**
     * 代理类名
     */
    private Class clazz;

    StaticMethodInvokeContext(Class clazz, String methodName, Object[] allArguments, Class<?>[] parameterTypes) {
        super(methodName, allArguments, parameterTypes);
        this.clazz = clazz;
    }

    /**
     *
     * @return the target class
     */
    public Class claszz() {
        return clazz;
    }
}
