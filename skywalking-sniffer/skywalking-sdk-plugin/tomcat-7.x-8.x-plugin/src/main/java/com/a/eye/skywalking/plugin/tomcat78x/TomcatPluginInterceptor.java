package com.a.eye.skywalking.plugin.tomcat78x;

import com.a.eye.skywalking.context.ContextCarrier;
import com.a.eye.skywalking.context.ContextManager;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TomcatPluginInterceptor implements InstanceMethodsAroundInterceptor {
    private static final String TOMCAT_OPERATION_NAME_PREFIX = "Web/Tomcat";
    private String tracingName = DEFAULT_TRACE_NAME;
    private static final String DEFAULT_TRACE_NAME = "SkyWalking-TRACING-NAME";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        Object[] args = interceptorContext.allArguments();
        HttpServletRequest request = (HttpServletRequest) args[0];
        String tracingHeaderValue = request.getHeader(tracingName);

        Span span = ContextManager.INSTANCE.createSpan(TOMCAT_OPERATION_NAME_PREFIX + request.getRequestURI());
        Tags.SPAN_KIND.asHttp(span);
        Tags.HTTP_URL.set(span, request.getRequestURL().toString());

        if (tracingHeaderValue != null) {
            ContextManager.INSTANCE.extract(new ContextCarrier().deserialize(tracingHeaderValue));
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        HttpServletResponse response = (HttpServletResponse) interceptorContext.allArguments()[1];
        Tags.HTTP_STATUS.set(ContextManager.INSTANCE.activeSpan(), response.getStatus());
        ContextManager.INSTANCE.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
                                      InstanceMethodInvokeContext interceptorContext) {
        ContextManager.INSTANCE.activeSpan().log(t);
    }

}
