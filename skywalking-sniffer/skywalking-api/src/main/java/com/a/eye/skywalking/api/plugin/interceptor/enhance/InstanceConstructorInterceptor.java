package com.a.eye.skywalking.api.plugin.interceptor.enhance;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;

/**
 * The instance constructor's interceptor interface.
 * Any plugin, which wants to intercept constructor, must implement this interface.
 * <p>
 * Created by wusheng on 2016/11/29.
 */
public interface InstanceConstructorInterceptor {
    /**
     * Called before the origin constructor invocation.
     *
     * @param context            a new added instance field
     * @param interceptorContext constructor invocation context.
     */
    void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext);
}
