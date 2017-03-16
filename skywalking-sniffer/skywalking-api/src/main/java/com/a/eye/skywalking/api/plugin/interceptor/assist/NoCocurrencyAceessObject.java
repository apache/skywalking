package com.a.eye.skywalking.api.plugin.interceptor.assist;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.InterceptorException;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;

/**
 * {@link NoCocurrencyAceessObject} is method invocation counter,
 * when {@link #whenEnter(EnhancedClassInstanceContext, Runnable)}, counter + 1;
 * and when {@link #whenExist(EnhancedClassInstanceContext, Runnable)}, counter -1;
 *
 * When, and only when, the first enter and last exist, also meaning first access, the Runnable is called.
 *
 * @author wusheng
 */
public class NoCocurrencyAceessObject {
    protected String invokeCounterKey = "__$invokeCounterKey";

    protected Object invokeCounterInstLock = new Object();

    public void whenEnter(EnhancedClassInstanceContext context, Runnable runnable) {
        if (!context.isContain(invokeCounterKey)) {
            synchronized (invokeCounterInstLock) {
                if (!context.isContain(invokeCounterKey)) {
                    context.set(invokeCounterKey, 0);
                }
            }
        }
        int counter = context.get(invokeCounterKey,
            Integer.class);
        if(++counter == 1){
            runnable.run();
        }
        context.set(invokeCounterKey, counter);
    }

    public void whenExist(EnhancedClassInstanceContext context, Runnable runnable) {
        if (!context.isContain(invokeCounterKey)) {
            throw new InterceptorException(
                "key=invokeCounterKey not found is context. unexpected situation.");
        }
        int counter = context.get(invokeCounterKey,
            Integer.class);
        if(--counter == 0){
            runnable.run();
        }
        context.set(invokeCounterKey, counter);
    }
}
