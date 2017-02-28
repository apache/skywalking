package com.a.eye.skywalking.plugin.httpClient.v4;


import com.a.eye.skywalking.api.context.ContextCarrier;
import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;

/**
 * {@link HttpClientExecuteInterceptor} transport the trace context by call {@link HttpRequest#setHeader(Header)},
 * The current span tag the {@link Tags#ERROR} if {@link StatusLine#getStatusCode()} is not equals 200.
 *
 * @author zhangxin
 */
public class HttpClientExecuteInterceptor implements InstanceMethodsAroundInterceptor {
    public static final String HEADER_NAME_OF_CONTEXT_DATA = "SWTraceContext";
    private static final String COMPONENT_NAME = "Http";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context,
                             InstanceMethodInvokeContext interceptorContext, MethodInterceptResult result) {
        Object[] allArguments = interceptorContext.allArguments();
        if (allArguments[0] == null || allArguments[1] == null) {
            // illegal args, can't trace. ignore.
            return;
        }
        HttpHost httpHost = (HttpHost) allArguments[0];
        HttpRequest httpRequest = (HttpRequest) allArguments[1];

        Span span = ContextManager.INSTANCE.createSpan(httpRequest.getRequestLine().getUri());
        Tags.PEER_PORT.set(span, httpHost.getPort());
        Tags.PEER_HOST.set(span, httpHost.getHostName());
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
        Tags.COMPONENT.set(span, COMPONENT_NAME);
        Tags.URL.set(span, generateURL(httpHost, httpRequest));
        Tags.SPAN_LAYER.asHttp(span);

        ContextCarrier contextCarrier = new ContextCarrier();
        ContextManager.INSTANCE.inject(contextCarrier);
        httpRequest.setHeader(HEADER_NAME_OF_CONTEXT_DATA, contextCarrier.serialize());
    }

    /**
     * Format request URL.
     *
     * @return request URL
     */
    private String generateURL(HttpHost httpHost, HttpRequest httpRequest) {
        return httpHost.getSchemeName() + "://" + httpHost.getHostName() + ":" + httpHost.getPort() + httpRequest.getRequestLine().getUri();
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context,
                              InstanceMethodInvokeContext interceptorContext, Object ret) {
        Object[] allArguments = interceptorContext.allArguments();
        if (allArguments[0] == null || allArguments[1] == null) {
            return ret;
        }

        HttpResponse response = (HttpResponse) ret;
        int statusCode = response.getStatusLine().getStatusCode();
        Span span = ContextManager.INSTANCE.activeSpan();
        if (statusCode != 200) {
            Tags.ERROR.set(span, true);
        }

        Tags.STATUS_CODE.set(span, statusCode);
        ContextManager.INSTANCE.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
        Tags.ERROR.set(ContextManager.INSTANCE.activeSpan(), true);
        ContextManager.INSTANCE.activeSpan().log(t);
    }

}
