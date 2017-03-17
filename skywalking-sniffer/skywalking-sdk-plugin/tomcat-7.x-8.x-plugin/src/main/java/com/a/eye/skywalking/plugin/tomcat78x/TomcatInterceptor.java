package com.a.eye.skywalking.plugin.tomcat78x;

import com.a.eye.skywalking.api.context.ContextCarrier;
import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@link TomcatInterceptor} fetch the serialized context data by using {@link HttpServletRequest#getHeader(String)}.
 * The {@link com.a.eye.skywalking.trace.TraceSegment#primaryRef} of current trace segment will reference to the trace segment id
 * of the previous level if the serialized context is not null.
 */
public class TomcatInterceptor implements InstanceMethodsAroundInterceptor {
    /**
     * Header name that the serialized context data stored in {@link HttpServletRequest#getHeader(String)}.
     */
    public static final String HEADER_NAME_OF_CONTEXT_DATA = "SWTraceContext";
    /**
     * Tomcat component.
     */
    public static final String TOMCAT_COMPONENT = "Tomcat";

    /**
     * The {@link com.a.eye.skywalking.trace.TraceSegment#primaryRef} of current trace segment will reference to the trace segment id
     * of the previous level if the serialized context is not null.
     *
     * @param context            instance context, a class instance only has one {@link EnhancedClassInstanceContext} instance.
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param result             change this result, if you want to truncate the method.
     */
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        Object[] args = interceptorContext.allArguments();
        HttpServletRequest request = (HttpServletRequest) args[0];

        Span span = ContextManager.createSpan(request.getRequestURI());
        Tags.COMPONENT.set(span, TOMCAT_COMPONENT);
        Tags.PEER_HOST.set(span, fetchRequestPeerHost(request));
        Tags.PEER_PORT.set(span, request.getRemotePort());
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
        Tags.URL.set(span, request.getRequestURL().toString());
        Tags.SPAN_LAYER.asHttp(span);

        String tracingHeaderValue = request.getHeader(HEADER_NAME_OF_CONTEXT_DATA);
        if (!StringUtil.isEmpty(tracingHeaderValue)) {
            ContextManager.extract(new ContextCarrier().deserialize(tracingHeaderValue));
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext, Object ret) {
        HttpServletResponse response = (HttpServletResponse) interceptorContext.allArguments()[1];

        Span span = ContextManager.activeSpan();
        Tags.STATUS_CODE.set(span, response.getStatus());

        if (response.getStatus() != 200) {
            Tags.ERROR.set(span, true);
        }

        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
                                      InstanceMethodInvokeContext interceptorContext) {
        Span span = ContextManager.activeSpan();
        span.log(t);
        Tags.ERROR.set(span, true);
    }

    /**
     *
     * @param request
     * @return
     */
    public String fetchRequestPeerHost(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

}
