package com.a.eye.skywalking.plugin.dubbo;

import com.a.eye.skywalking.api.context.ContextCarrier;
import com.a.eye.skywalking.api.context.ContextManager;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.plugin.dubbox.BugFixActive;
import com.a.eye.skywalking.plugin.dubbox.SWBaseBean;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;

/**
 * {@link DubboInterceptor} define how to enhance class {@link com.alibaba.dubbo.monitor.support.MonitorFilter#invoke(Invoker, Invocation)}.
 * the trace context transport to the provider side by {@link RpcContext#attachments}.but all the version of dubbo framework below 2.8.3
 * don't support {@link RpcContext#attachments}, we support another way to support it. it is that all request parameters of dubbo service
 * need to extend {@link SWBaseBean}, and {@link DubboInterceptor} will inject the trace context data to the {@link SWBaseBean} bean and
 * extract the trace context data from {@link SWBaseBean}, or the trace context data will not transport to the provider side.
 *
 * @author zhangxin
 */
public class DubboInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String ATTACHMENT_NAME_OF_CONTEXT_DATA = "SWTraceContext";
    public static final String DUBBO_COMPONENT = "Dubbo";

    /**
     * <h2>Consumer:</h2>
     * The serialized trace context data will inject the first param that extend {@link SWBaseBean} of dubbo service
     * if the method {@link BugFixActive#active()} be called. or the serialized context data will inject to the
     * {@link RpcContext#attachments} for transport to provider side.
     *
     * <h2>Provider:</h2>
     * The serialized trace context data will extract from the first param that extend {@link SWBaseBean} of dubbo service
     * if the method {@link BugFixActive#active()} be called. or it will extract from {@link RpcContext#attachments}.
     * current trace segment will ref if the serialize context data is not null.
     */
    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {
        Object[] arguments = interceptorContext.allArguments();
        Invoker invoker = (Invoker) arguments[0];
        Invocation invocation = (Invocation) arguments[1];
        RpcContext rpcContext = RpcContext.getContext();
        boolean isConsumer = rpcContext.isConsumerSide();
        URL requestURL = invoker.getUrl();

        Span span = ContextManager.INSTANCE.createSpan(generateOperationName(requestURL, invocation));
        Tags.URL.set(span, generateRequestURL(requestURL, invocation));
        Tags.COMPONENT.set(span, DUBBO_COMPONENT);
        Tags.PEER_HOST.set(span, requestURL.getHost());
        Tags.PEER_PORT.set(span, requestURL.getPort());
        Tags.SPAN_LAYER.asRPCFramework(span);

        if (isConsumer) {
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
            ContextCarrier contextCarrier = new ContextCarrier();
            ContextManager.INSTANCE.inject(contextCarrier);
            if (!BugFixActive.isActive()) {
                //invocation.getAttachments().put("contextData", contextDataStr);
                //@see https://github.com/alibaba/dubbo/blob/dubbo-2.5.3/dubbo-rpc/dubbo-rpc-api/src/main/java/com/alibaba/dubbo/rpc/RpcInvocation.java#L154-L161
                rpcContext.getAttachments().put(ATTACHMENT_NAME_OF_CONTEXT_DATA, contextCarrier.serialize());
            } else {
                fix283SendNoAttachmentIssue(invocation, contextCarrier);
            }
        } else {
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);

            ContextCarrier contextCarrier;
            if (!BugFixActive.isActive()) {
                contextCarrier = new ContextCarrier().deserialize(rpcContext.getAttachment(ATTACHMENT_NAME_OF_CONTEXT_DATA));
            } else {
                contextCarrier = fix283RecvNoAttachmentIssue(invocation);
            }

            if (contextCarrier != null) {
                ContextManager.INSTANCE.extract(contextCarrier);
            }
        }
    }

    /**
     * Execute after {@link com.alibaba.dubbo.monitor.support.MonitorFilter#invoke(Invoker, Invocation)},
     * when dubbo instrumentation is active。Check {@link Result#getException()} , if not NULL,
     * log the exception and set tag error=true.
     */
    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                              Object ret) {
        Result result = (Result) ret;
        if (result != null && result.getException() != null) {
            dealException(result.getException());
        }

        ContextManager.INSTANCE.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
                                      InstanceMethodInvokeContext interceptorContext) {
        dealException(t);
    }

    /**
     * Log the throwable, which occurs in Dubbo RPC service.
     */
    private void dealException(Throwable throwable) {
        Span span = ContextManager.INSTANCE.activeSpan();
        Tags.ERROR.set(span, true);
        span.log(throwable);
    }

    /**
     * Format operation name. e.g. com.a.eye.skywalking.plugin.test.Test.test(String)
     *
     * @return operation name.
     */
    private String generateOperationName(URL requestURL, Invocation invocation) {
        StringBuilder operationName = new StringBuilder();
        operationName.append(requestURL.getPath());
        operationName.append("." + invocation.getMethodName() + "(");
        for (Class<?> classes : invocation.getParameterTypes()) {
            operationName.append(classes.getSimpleName() + ",");
        }

        if (invocation.getParameterTypes().length > 0) {
            operationName.delete(operationName.length() - 1, operationName.length());
        }

        operationName.append(")");

        return operationName.toString();
    }

    /**
     * Format request url.
     * e.g. dubbo://127.0.0.1:20880/com.a.eye.skywalking.plugin.test.Test.test(String).
     *
     * @return request url.
     */
    private String generateRequestURL(URL url, Invocation invocation) {
        StringBuilder requestURL = new StringBuilder();
        requestURL.append(url.getProtocol() + "://");
        requestURL.append(url.getHost());
        requestURL.append(":" + url.getPort() + "/");
        requestURL.append(generateOperationName(url, invocation));
        return requestURL.toString();
    }

    /**
     * Set the trace context.
     *
     * @param contextCarrier {@link ContextCarrier}.
     */
    private void fix283SendNoAttachmentIssue(Invocation invocation, ContextCarrier contextCarrier) {
        for (Object parameter : invocation.getArguments()) {
            if (parameter instanceof SWBaseBean) {
                ((SWBaseBean) parameter).setTraceContext(contextCarrier.serialize());
                return;
            }
        }
    }

    /**
     * Fetch the trace context by using {@link Invocation#getArguments()}.
     *
     * @return trace context data.
     */
    private ContextCarrier fix283RecvNoAttachmentIssue(Invocation invocation) {
        for (Object parameter : invocation.getArguments()) {
            if (parameter instanceof SWBaseBean) {
                return new ContextCarrier().deserialize(((SWBaseBean) parameter).getTraceContext());
            }
        }

        return null;
    }
}
