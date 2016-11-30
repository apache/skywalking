package com.a.eye.skywalking.plugin.interceptor.enhance;

import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;

/**
 * Created by wusheng on 2016/11/29.
 */
public interface InstanceConstructorInterceptor {
    void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext);
}
