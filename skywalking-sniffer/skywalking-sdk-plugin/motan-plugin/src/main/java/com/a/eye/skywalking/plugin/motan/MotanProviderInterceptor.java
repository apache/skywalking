package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.api.context.ContextCarrier;
import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;
import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;

/**
 * Current trace segment will ref the trace segment from previous level if the serialized context data that fetch
 * from {@link Request#getAttachments()} is not null.
 *
 * {@link MotanProviderInterceptor} intercept all constructor of {@link com.weibo.api.motan.rpc.AbstractProvider} for record
 * the request url from consumer side.
 *
 * @author zhangxin
 */
public class MotanProviderInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {

    /**
     * The
     */
    private static final String KEY_NAME_OF_REQUEST_URL = "REQUEST_URL";

    /**
     * The {@link Request#getAttachments()} key. It maps to the serialized {@link ContextCarrier}.
     */
    private static final String ATTACHMENT_KEY_OF_CONTEXT_DATA = "contextData";
    /**
     * Motan component
     */
    private static final String MOTAN_COMPONENT = "Motan";

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        context.set(KEY_NAME_OF_REQUEST_URL, interceptorContext.allArguments()[0]);
    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {
        URL url = (URL) context.get(KEY_NAME_OF_REQUEST_URL);
        if (url != null) {
            com.weibo.api.motan.rpc.Request request = (com.weibo.api.motan.rpc.Request) interceptorContext.allArguments()[0];
            Span span = ContextManager.INSTANCE.createSpan(generateViewPoint(url, request));
            Tags.COMPONENT.set(span, MOTAN_COMPONENT);
            Tags.URL.set(span, url.getIdentity());
            Tags.PEER_PORT.set(span, url.getPort());
            Tags.PEER_HOST.set(span, url.getHost());
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
            Tags.SPAN_LAYER.asRPCFramework(span);

            String serializedContextData = request.getAttachments().get(ATTACHMENT_KEY_OF_CONTEXT_DATA);
            ContextManager.INSTANCE.extract(new ContextCarrier().deserialize(serializedContextData));
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                              Object ret) {
        Response response = (Response) ret;
        if (response != null && response.getException() != null) {
            Span span = ContextManager.INSTANCE.activeSpan();
            Tags.ERROR.set(span, true);
            span.log(response.getException());
        }
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
                                      InstanceMethodInvokeContext interceptorContext) {
        ContextManager.INSTANCE.activeSpan().log(t);
    }


    private static String generateViewPoint(URL serviceURI, Request request) {
        StringBuilder viewPoint = new StringBuilder(serviceURI.getUri());
        viewPoint.append("." + request.getMethodName());
        viewPoint.append("(" + request.getParamtersDesc() + ")");
        return viewPoint.toString();
    }

}
