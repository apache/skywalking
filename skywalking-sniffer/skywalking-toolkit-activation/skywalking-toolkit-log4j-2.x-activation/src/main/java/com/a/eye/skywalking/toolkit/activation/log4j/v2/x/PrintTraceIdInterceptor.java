package com.a.eye.skywalking.toolkit.activation.log4j.v2.x;

import com.a.eye.skywalking.api.Tracing;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.*;

/**
 * Created by wusheng on 2016/12/7.
 */
public class PrintTraceIdInterceptor implements StaticMethodsAroundInterceptor {
    @Override
    public void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        ((StringBuilder)interceptorContext.allArguments()[0]).append("TID:" + Tracing.getTraceId());

        //make sure origin method do not invoke.
        result.defineReturnValue(null);
    }

    @Override
    public Object afterMethod(StaticMethodInvokeContext interceptorContext, Object ret) {
        return null;
    }

    @Override
    public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext) {

    }
}
