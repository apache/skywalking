package com.a.eye.skywalking.api.plugin.interceptor.enhance;

/**
 * The static method's interceptor interface.
 * Any plugin, which wants to intercept static methods, must implement this interface.
 *
 * @author wusheng
 */
public interface StaticMethodsAroundInterceptor {
    /**
     * called before target method invocation.
     *
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param result change this result, if you want to truncate the method.
     */
    void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result);

    /**
     * called after target method invocation. Even method's invocation triggers an exception.
     *
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param ret the method's original return value.
     * @return the method's actual return value.
     */
    Object afterMethod(StaticMethodInvokeContext interceptorContext, Object ret);

    /**
     * called when occur exception.
     *
     * @param t the exception occur.
     * @param interceptorContext method context, includes class name, method name, etc.
     */
    void handleMethodException(Throwable t, MethodInvokeContext interceptorContext);
}
