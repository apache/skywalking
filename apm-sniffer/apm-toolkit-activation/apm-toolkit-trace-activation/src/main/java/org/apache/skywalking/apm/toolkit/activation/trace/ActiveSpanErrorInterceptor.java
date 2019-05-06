package org.apache.skywalking.apm.toolkit.activation.trace;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

import java.lang.reflect.Method;

/**
 * @author caoyixiong
 */
public class ActiveSpanErrorInterceptor implements StaticMethodsAroundInterceptor {
    @Override public void beforeMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
                                       MethodInterceptResult result) {
        AbstractSpan activeSpan = null;
        try {
            activeSpan = ContextManager.activeSpan();
            activeSpan.errorOccurred();
        } catch (NullPointerException e) {
        }
    }

    @Override public Object afterMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
                                        Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
                                      Throwable t) {

    }
}