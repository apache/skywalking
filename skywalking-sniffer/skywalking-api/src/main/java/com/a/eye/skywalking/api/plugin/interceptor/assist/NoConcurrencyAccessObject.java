package com.a.eye.skywalking.api.plugin.interceptor.assist;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.InterceptorException;

/**
 * {@link NoConcurrencyAccessObject} is method invocation counter,
 * when {@link #whenEnter(EnhancedClassInstanceContext, Runnable)}, counter + 1;
 * and when {@link #whenExist(EnhancedClassInstanceContext, Runnable)}, counter -1;
 *
 * When, and only when, the first enter and last exist, also meaning first access, the Runnable is called.
 *
 * @author wusheng
 */
public class NoConcurrencyAccessObject {
    private static final String INVOKE_COUNTER_KEY = "__$invokeCounterKey";

    public void whenEnter(EnhancedClassInstanceContext context, Runnable runnable) {
        if (!context.isContain(INVOKE_COUNTER_KEY)) {
            context.set(INVOKE_COUNTER_KEY, 0);
        }
        int counter = context.get(INVOKE_COUNTER_KEY,
            Integer.class);
        if (++counter == 1) {
            runnable.run();
        }
        context.set(INVOKE_COUNTER_KEY, counter);
    }

    public void whenExist(EnhancedClassInstanceContext context, Runnable runnable) {
        if (!context.isContain(INVOKE_COUNTER_KEY)) {
            throw new InterceptorException(
                "key=INVOKE_COUNTER_KEY not found is context. unexpected situation.");
        }
        int counter = context.get(INVOKE_COUNTER_KEY,
            Integer.class);
        if (--counter == 0) {
            runnable.run();
        }
        context.set(INVOKE_COUNTER_KEY, counter);
    }
}
