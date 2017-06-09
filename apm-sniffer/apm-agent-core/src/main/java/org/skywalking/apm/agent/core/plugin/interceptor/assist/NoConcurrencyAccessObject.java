package org.skywalking.apm.agent.core.plugin.interceptor.assist;

import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.InterceptorException;

/**
 * {@link NoConcurrencyAccessObject} is method invocation counter,
 * when {@link #whenEnter(EnhancedClassInstanceContext, InstanceMethodInvokeContext)} , counter + 1;
 * and when {@link #whenExist(EnhancedClassInstanceContext)}  , counter -1;
 * <p>
 * When, and only when, the first enter and last exist, also meaning first access,
 * the {@link #enter(EnhancedClassInstanceContext, InstanceMethodInvokeContext)}
 * and {@link #exit()} are called.
 *
 * @author wusheng
 */
public abstract class NoConcurrencyAccessObject {
    private static final String INVOKE_COUNTER_KEY = "__$invokeCounterKey";

    public void whenEnter(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
        if (!context.isContain(INVOKE_COUNTER_KEY)) {
            context.set(INVOKE_COUNTER_KEY, 0);
        }
        int counter = (Integer)context.get(INVOKE_COUNTER_KEY);
        if (++counter == 1) {
            enter(context, interceptorContext);
        }
        context.set(INVOKE_COUNTER_KEY, counter);
    }

    public void whenExist(EnhancedClassInstanceContext context) {
        if (!context.isContain(INVOKE_COUNTER_KEY)) {
            throw new InterceptorException(
                "key=INVOKE_COUNTER_KEY not found is context. unexpected situation.");
        }
        int counter = (Integer)context.get(INVOKE_COUNTER_KEY);
        if (--counter == 0) {
            exit();
        }
        context.set(INVOKE_COUNTER_KEY, counter);
    }

    protected abstract void enter(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext);

    protected abstract void exit();
}
