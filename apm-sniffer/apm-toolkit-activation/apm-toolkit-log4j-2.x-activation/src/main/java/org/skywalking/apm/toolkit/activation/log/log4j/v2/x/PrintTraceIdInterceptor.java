package org.skywalking.apm.toolkit.activation.log.log4j.v2.x;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

/**
 * Created by wusheng on 2016/12/7.
 */
public class PrintTraceIdInterceptor implements StaticMethodsAroundInterceptor {
    /**
     * Override org.skywalking.apm.toolkit.log.log4j.v2.x.Log4j2OutputAppender.append(),
     *
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param result             change this result, to output the traceId. The origin append() method will not invoke.
     */
    @Override
    public void beforeMethod(StaticMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        ((StringBuilder) interceptorContext.allArguments()[0]).append("TID:" + ContextManager.getGlobalTraceId());

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
