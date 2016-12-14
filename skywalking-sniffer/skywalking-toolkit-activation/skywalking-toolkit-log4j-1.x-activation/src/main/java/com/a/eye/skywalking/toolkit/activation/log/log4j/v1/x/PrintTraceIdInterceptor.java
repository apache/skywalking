package com.a.eye.skywalking.toolkit.activation.log.log4j.v1.x;

import com.a.eye.skywalking.api.Tracing;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * Created by wusheng on 2016/12/7.
 */
public class PrintTraceIdInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        return "TID:" + Tracing.getTraceId();
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {

    }
}
