package org.skywalking.apm.plugin.resin.v3x.v4x;


import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.api.context.ContextCarrier;
import org.skywalking.apm.api.context.ContextManager;
import org.skywalking.apm.api.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.api.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.api.util.StringUtil;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.tag.Tags;

/**
 * {@link ResinInterceptor} intercept method of{@link com.caucho.server.dispatch.ServletInvocation#service(javax.servlet.ServletRequest,
 * javax.servlet.ServletResponse)} record the resin host, port ,url.
 *
 * @author baiyang
 */
public class ResinInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * Header name that the serialized context data stored in
     * {@link HttpServletRequest#getHeader(String)}.
     */
    public static final String HEADER_NAME_OF_CONTEXT_DATA = "SWTraceContext";
    /**
     * Resin component.
     */
    public static final String RESIN_COMPONENT = "Resin";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        MethodInterceptResult result) {
        Object[] args = interceptorContext.allArguments();
        HttpServletRequest request = (HttpServletRequest)args[0];
        Span span = ContextManager.createSpan(request.getRequestURI());
        Tags.COMPONENT.set(span, RESIN_COMPONENT);
        Tags.PEER_HOST.set(span, request.getServerName());
        Tags.PEER_PORT.set(span, request.getServerPort());
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
        Tags.URL.set(span, request.getRequestURL().toString());
        Tags.SPAN_LAYER.asHttp(span);

        String tracingHeaderValue = request.getHeader(HEADER_NAME_OF_CONTEXT_DATA);
        if (!StringUtil.isEmpty(tracingHeaderValue)) {
            ContextManager.extract(new ContextCarrier().deserialize(tracingHeaderValue));
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
        Object ret) {
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

}
