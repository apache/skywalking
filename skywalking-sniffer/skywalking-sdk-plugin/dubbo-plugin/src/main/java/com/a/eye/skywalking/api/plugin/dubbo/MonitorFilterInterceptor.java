package com.a.eye.skywalking.plugin.dubbo;

import com.a.eye.skywalking.context.ContextCarrier;
import com.a.eye.skywalking.context.ContextManager;
import com.a.eye.skywalking.plugin.dubbox.bugfix.below283.BugFixAcitve;
import com.a.eye.skywalking.plugin.dubbox.bugfix.below283.SWBaseBean;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;
import com.a.eye.skywalking.util.StringUtil;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;

/**
 *
 */
public class MonitorFilterInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String IS_CONSUMER_FLAG = "isConsumer";
    private static final String DUBBO_COMPONENT = "Dubbo";
    private static final String ATTACHMENT_KEY_OF_CONTEXT_CARRIER_DATA = "contextData";

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
                             MethodInterceptResult result) {
        DubboRequestParam dubboRequestParam = new DubboRequestParam(interceptorContext);
        context.set(IS_CONSUMER_FLAG, dubboRequestParam.isConsumer);
        Span span = ContextManager.INSTANCE.createSpan(dubboRequestParam.fetchOperationName());

        if (dubboRequestParam.isConsumer) {
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);

            ContextCarrier contextCarrier = new ContextCarrier();
            ContextManager.INSTANCE.extract(contextCarrier);

            //追加参数
            if (!BugFixAcitve.isActive) {
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
                dubboRequestParam.rpcContext.getAttachments().put(ATTACHMENT_KEY_OF_CONTEXT_CARRIER_DATA, contextCarrier.serialize());
            } else {
                fix283SendNoAttachmentIssue(dubboRequestParam.invocation, contextCarrier.serialize());
            }
        } else {
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);

            String contextDataStr = fetchContextSerializeData(dubboRequestParam.invocation, dubboRequestParam.rpcContext);
            if (!StringUtil.isEmpty(contextDataStr)) {
                ContextManager.INSTANCE.inject(new ContextCarrier().deserialize(contextDataStr));
            }
        }

        Tags.URL.set(span, dubboRequestParam.fetchRequestURL());
        Tags.COMPONENT.set(span, DUBBO_COMPONENT);
        Tags.SPAN_LAYER.asRPCFramework(span);
    }

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

    private String fetchContextSerializeData(Invocation invocation, RpcContext rpcContext) {
        String contextDataStr;
        if (!BugFixAcitve.isActive) {
            contextDataStr = rpcContext.getAttachment(ATTACHMENT_KEY_OF_CONTEXT_CARRIER_DATA);
        } else {
            contextDataStr = fix283RecvNoAttachmentIssue(invocation);
        }
        return contextDataStr;
    }


    private void dealException(Throwable t) {
        ContextManager.INSTANCE.activeSpan().log(t);
    }


    private static void fix283SendNoAttachmentIssue(Invocation invocation, String contextDataStr) {

        for (Object parameter : invocation.getArguments()) {
            if (parameter instanceof SWBaseBean) {
                ((SWBaseBean) parameter).setContextData(contextDataStr);
                return;
            }
        }
    }

    private static String fix283RecvNoAttachmentIssue(Invocation invocation) {
        for (Object parameter : invocation.getArguments()) {
            if (parameter instanceof SWBaseBean) {
                return ((SWBaseBean) parameter).getContextData();
            }
        }

        return null;
    }

    class DubboRequestParam {
        final boolean isConsumer;
        final RpcContext rpcContext;
        final Invocation invocation;
        final Invoker invoker;

        public DubboRequestParam(InstanceMethodInvokeContext interceptorContext) {
            Object[] arguments = interceptorContext.allArguments();
            rpcContext = RpcContext.getContext();
            isConsumer = rpcContext.isConsumerSide();
            invocation = (Invocation) arguments[1];
            invoker = (Invoker) arguments[0];
        }

        public String fetchOperationName() {
            StringBuilder operationName = new StringBuilder();
            operationName.append(invoker.getUrl().getAbsolutePath());
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

        public String fetchRequestURL() {
            StringBuilder operationName = new StringBuilder();
            operationName.append(invoker.getUrl().getProtocol() + "://");
            operationName.append(invoker.getUrl().getHost());
            operationName.append(":" + invoker.getUrl().getPort());
            operationName.append(fetchOperationName());
            return operationName.toString();
        }
    }
}
