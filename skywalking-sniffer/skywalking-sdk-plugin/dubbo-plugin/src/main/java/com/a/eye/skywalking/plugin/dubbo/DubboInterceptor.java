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
 * the context data will transport to the provider side by {@link RpcContext#attachments}.but all the version of dubbo framework below 2.8.3
 * don't support {@link RpcContext#attachments}, we support another way to support it. it is that all request parameters of dubbo service
 * need to extend {@link SWBaseBean}, and {@link DubboInterceptor} will inject the serialized context data to the {@link SWBaseBean} bean and
 * extract the serialized context data from {@link SWBaseBean}, or the context data will not transport to the provider side.
 *
 * @author zhangxin
 */
public class DubboInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String ATTACHMENT_NAME_OF_CONTEXT_DATA = "contextData";
    public static final String DUBBO_COMPONENT = "Dubbo";

    /**
     * <h2>Consumer:</h2>
     * The serialized context data will inject the first param that extend {@link SWBaseBean} of dubbo service
     * if the method {@link BugFixActive#active()} be called. or the serialized context data will inject to the
     * {@link RpcContext#attachments} for transport to provider side.
     *
     * <h2>Provider:</h2>
     * The serialized context data will extract from the first param that extend {@link SWBaseBean} of dubbo service
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
                // context.setAttachment("contextData", contextDataStr);
                // context的setAttachment方法在重试机制的时候并不会覆盖原有的Attachment
                // 参见Dubbo源代码：“com.alibaba.dubbo.rpc.RpcInvocation”
                //  public void setAttachmentIfAbsent(String key, String value) {
                //      if (attachments == null) {
                //          attachments = new HashMap<String, String>();
                //      }
                //      if (! attachments.containsKey(key)) {
                //          attachments.put(key, value);
                //      }
                //  }
                // 在Rest模式中attachment会被抹除，不会传入到服务端
                // Rest模式会将attachment存放到header里面，具体见com.alibaba.dubbo.rpc.protocol.rest.RpcContextFilter
                //invocation.getAttachments().put("contextData", contextDataStr);
                rpcContext.getAttachments().put(ATTACHMENT_NAME_OF_CONTEXT_DATA, contextCarrier.serialize());
            } else {
                fix283SendNoAttachmentIssue(invocation, contextCarrier.serialize());
            }
        } else {
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);

            String contextDataStr;
            if (!BugFixActive.isActive()) {
                contextDataStr = rpcContext.getAttachment(ATTACHMENT_NAME_OF_CONTEXT_DATA);
            } else {
                contextDataStr = fix283RecvNoAttachmentIssue(invocation);
            }

            if (contextDataStr != null && contextDataStr.length() > 0) {
                ContextManager.INSTANCE.extract(new ContextCarrier().deserialize(contextDataStr));
            }
        }
    }

    /**
     * {@link DubboInterceptor#afterMethod(EnhancedClassInstanceContext, InstanceMethodInvokeContext, Object)} be executed after
     * {@link com.alibaba.dubbo.monitor.support.MonitorFilter#invoke(Invoker, Invocation)}, and it will check {@link Result#getException()} if is null.
     * current active span will log the exception and set true to the value of error tag if the {@link Result#getException()} is not null.
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
     * Active span will log the exception and set current span value of error tag.
     */
    private void dealException(Throwable throwable) {
        Span span = ContextManager.INSTANCE.activeSpan();
        Tags.ERROR.set(span, true);
        span.log(throwable);
    }

    /**
     * Generate operation name.
     * the operation name should be like this <code>com.a.eye.skywalking.plugin.test.Test.test(String)</code>.
     *
     * @return operation name.
     */
    private static String generateOperationName(URL requestURL, Invocation invocation) {
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
     * Generate request url.
     * The request url may be like this <code>dubbo://127.0.0.1:20880/com.a.eye.skywalking.plugin.test.Test.test(String)</code>.
     *
     * @return request url.
     */
    private static String generateRequestURL(URL url, Invocation invocation) {
        StringBuilder requestURL = new StringBuilder();
        requestURL.append(url.getProtocol() + "://");
        requestURL.append(url.getHost());
        requestURL.append(":" + url.getPort() + "/");
        requestURL.append(generateOperationName(url, invocation));
        return requestURL.toString();
    }

    /**
     * Set the serialized context data to the first request param that extend {@link SWBaseBean} of dubbo service.
     *
     * @param contextDataStr serialized context data.
     */
    private static void fix283SendNoAttachmentIssue(Invocation invocation, String contextDataStr) {
        for (Object parameter : invocation.getArguments()) {
            if (parameter instanceof SWBaseBean) {
                ((SWBaseBean) parameter).setContextData(contextDataStr);
                return;
            }
        }
    }

    /**
     * Fetch the serialize context data from the first request param that extend {@link SWBaseBean} of dubbo service.
     *
     * @return serialized context data.
     */
    private static String fix283RecvNoAttachmentIssue(Invocation invocation) {
        for (Object parameter : invocation.getArguments()) {
            if (parameter instanceof SWBaseBean) {
                return ((SWBaseBean) parameter).getContextData();
            }
        }

        return null;
    }
}
