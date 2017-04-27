package org.skywalking.apm.toolkit.activation.log.log4j.v1.x;

import org.skywalking.apm.api.context.ContextManager;
import org.skywalking.apm.api.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.api.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * Created by wusheng on 2016/12/7.
 */
public class PrintTraceIdInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {

    }

    /**
     * Override org.skywalking.apm.toolkit.log.log4j.v1.x.TraceIdPatternConverter.convert(),
     *
     * @param context            instance context, a class instance only has one {@link EnhancedClassInstanceContext} instance.
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param ret                the method's original return value.
     * @return the traceId
     */
    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                              Object ret) {
        return "TID:" + ContextManager.getGlobalTraceId();
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
                                      InstanceMethodInvokeContext interceptorContext) {

    }
}
