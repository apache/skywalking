package com.a.eye.skywalking.toolkit.activation.log.log4j.v2.x;

import com.a.eye.skywalking.api.context.ContextCarrier;
import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.StaticMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

/**
 * Created by wusheng on 2016/12/7.
 */
public class PrintTraceIdInterceptor implements StaticMethodsAroundInterceptor {
    /**
     * Override com.a.eye.skywalking.toolkit.log.log4j.v2.x.Log4j2OutputAppender.append(),
     *
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param result             change this result, to output the traceId. The origin append() method will not invoke.
     */
    @Override
    public void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        ((StringBuilder) interceptorContext.allArguments()[0]).append("TID:" + ContextManager.INSTANCE.getTraceSegmentId());

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
