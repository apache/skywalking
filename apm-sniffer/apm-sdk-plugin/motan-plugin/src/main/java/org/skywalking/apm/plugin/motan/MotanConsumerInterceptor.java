package org.skywalking.apm.plugin.motan;

import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;
import org.skywalking.apm.api.context.ContextCarrier;
import org.skywalking.apm.api.context.ContextManager;
import org.skywalking.apm.api.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.api.plugin.interceptor.enhance.*;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.tag.Tags;

/**
 * {@link MotanProviderInterceptor} create span by fetch request url from
 * {@link EnhancedClassInstanceContext#context} and transport serialized context
 * data to provider side through {@link Request#setAttachment(String, String)}.
 *
 * @author zhangxin
 */
public class MotanConsumerInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {

    /**
     * The
     */
    private static final String KEY_NAME_OF_REQUEST_URL = "REQUEST_URL";

    /**
     * The {@link Request#getAttachments()} key. It maps to the serialized {@link ContextCarrier}.
     */
    private static final String ATTACHMENT_KEY_OF_CONTEXT_DATA = "SWTraceContext";
    /**
     * Motan component
     */
    private static final String MOTAN_COMPONENT = "Motan";

    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        context.set(KEY_NAME_OF_REQUEST_URL, interceptorContext.allArguments()[1]);
    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {
        URL url = (URL) context.get(KEY_NAME_OF_REQUEST_URL);
        Request request = (Request) interceptorContext.allArguments()[0];
        if (url != null) {
            Span span = ContextManager.createSpan(generateOperationName(url, request));
            Tags.PEER_HOST.set(span, url.getHost());
            Tags.PEER_PORT.set(span, url.getPort());
            Tags.COMPONENT.set(span, MOTAN_COMPONENT);
            Tags.URL.set(span, url.getIdentity());
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
            Tags.SPAN_LAYER.asRPCFramework(span);

            ContextCarrier contextCarrier = new ContextCarrier();
            ContextManager.inject(contextCarrier);
            request.setAttachment(ATTACHMENT_KEY_OF_CONTEXT_DATA, contextCarrier.serialize());
        }
    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                              Object ret) {
        Response response = (Response) ret;
        if (response != null && response.getException() != null) {
            Span span = ContextManager.activeSpan();
            Tags.ERROR.set(span, true);
            span.log(response.getException());
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
                                      InstanceMethodInvokeContext interceptorContext) {
        ContextManager.activeSpan().log(t);
    }

    /**
     * Generate operation name.
     *
     * @return operation name.
     */
    private static String generateOperationName(URL serviceURI, Request request) {
        return new StringBuilder(serviceURI.getPath()).append(".").append(request.getMethodName()).append("(")
            .append(request.getParamtersDesc()).append(")").toString();
    }

}
