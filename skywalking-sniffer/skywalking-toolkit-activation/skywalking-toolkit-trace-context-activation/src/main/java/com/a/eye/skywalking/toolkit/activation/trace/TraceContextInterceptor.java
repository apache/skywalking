package com.a.eye.skywalking.toolkit.activation.trace;

import com.a.eye.skywalking.api.Tracing;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.StaticMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

/**
 *
 *
 * Created by xin on 2016/12/15.
 */
public class TraceContextInterceptor implements StaticMethodsAroundInterceptor {

    private ILog logger = LogManager.getLogger(TraceContextInterceptor.class);

    @Override
    public void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(StaticMethodInvokeContext interceptorContext, Object ret) {
       return Tracing.getTraceId();
    }

    @Override
    public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext) {
        logger.error("Failed to get trace Id.", t);
    }
}
