package com.a.eye.skywalking.toolkit.activation.trace;

import com.a.eye.skywalking.api.context.ContextCarrier;
import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.logging.ILog;
import com.a.eye.skywalking.api.logging.LogManager;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.StaticMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

/**
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
        ContextCarrier carrier = new ContextCarrier();
        ContextManager.INSTANCE.inject(carrier);
       return carrier.getTraceSegmentId();
    }

    @Override
    public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext) {
        logger.error("Failed to get trace Id.", t);
    }
}
