package org.skywalking.apm.toolkit.activation.trace;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

/**
 * Created by xin on 2016/12/15.
 */
public class TraceContextInterceptor implements StaticMethodsAroundInterceptor {

    private ILog logger = LogManager.getLogger(TraceContextInterceptor.class);

    @Override
    public void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(StaticMethodInvokeContext interceptorContext, Object ret) {
        return ContextManager.getGlobalTraceId();
    }

    @Override
    public void handleMethodException(Throwable t, MethodInvokeContext interceptorContext) {
        logger.error("Failed to get trace Id.", t);
    }
}
