package com.a.eye.skywalking.api.plugin.interceptor.assist;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.InterceptorException;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;

/**
 * {@link NoCocurrencyAceessObject} is an abstract class,
 * works for class's methods call each others, which these methods should be intercepted.
 *
 * At this scenario, only the first access should be intercepted.
 *
 * @author wusheng
 */
public abstract class NoCocurrencyAceessObject implements InstanceMethodsAroundInterceptor {
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
