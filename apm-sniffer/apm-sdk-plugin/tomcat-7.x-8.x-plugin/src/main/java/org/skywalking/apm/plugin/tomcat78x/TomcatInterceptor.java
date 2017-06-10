package org.skywalking.apm.plugin.tomcat78x;

import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.util.StringUtil;
import org.skywalking.apm.agent.core.context.trace.Span;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.tag.Tags;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@link TomcatInterceptor} fetch the serialized context data by using {@link HttpServletRequest#getHeader(String)}.
 * The {@link TraceSegment#refs} of current trace segment will reference to the trace
 * segment id of the previous level if the serialized context is not null.
 */
public class TomcatInterceptor implements InstanceMethodsAroundInterceptor {
    /**
     * Tomcat component.
     */
    public static final String TOMCAT_COMPONENT = "Tomcat";

    /**
     * The {@link TraceSegment#refs} of current trace segment will reference to the
     * trace segment id of the previous level if the serialized context is not null.
     *
     * @param context            instance context, a class instance only has one {@link EnhancedClassInstanceContext} instance.
     * @param interceptorContext method context, includes class name, method name, etc.
     * @param result             change this result, if you want to truncate the method.
     */
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {
        Object[] args = interceptorContext.allArguments();
        HttpServletRequest request = (HttpServletRequest) args[0];

        Span span = ContextManager.createSpan(request.getRequestURI());
        Tags.COMPONENT.set(span, TOMCAT_COMPONENT);
        span.setPeerHost(fetchRequestPeerHost(request));
        span.setPort(request.getRemotePort());
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
        Tags.URL.set(span, request.getRequestURL().toString());
        Tags.SPAN_LAYER.asHttp(span);

        String tracingHeaderValue = request.getHeader(Config.Plugin.Propagation.HEADER_NAME);
        if (!StringUtil.isEmpty(tracingHeaderValue)) {
            ContextManager.extract(new ContextCarrier().deserialize(tracingHeaderValue));
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                              Object ret) {
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
