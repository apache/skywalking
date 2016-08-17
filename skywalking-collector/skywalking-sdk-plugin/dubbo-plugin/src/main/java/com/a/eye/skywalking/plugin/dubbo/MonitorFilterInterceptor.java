package com.a.eye.skywalking.plugin.dubbo;

import com.a.eye.skywalking.invoke.monitor.RPCServerInvokeMonitor;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.plugin.dubbox.bugfix.below283.BugFixAcitve;
import com.a.eye.skywalking.plugin.dubbox.bugfix.below283.SWBaseBean;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.invoke.monitor.RPCClientInvokeMonitor;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;

public class MonitorFilterInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void onConstruct(EnhancedClassInstanceContext context, ConstructorInvokeContext interceptorContext) {
        // do nothing
    }

    @Override
    public void beforeMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            MethodInterceptResult result) {
        Object[] arguments = interceptorContext.allArguments();
        Invoker invoker = (Invoker) arguments[0];
        Invocation invocation = (Invocation) arguments[1];

        RpcContext rpcContext = RpcContext.getContext();
        boolean isConsumer = rpcContext.isConsumerSide();
        context.set("isConsumer", isConsumer);
        if (isConsumer) {
            ContextData contextData =
                    new RPCClientInvokeMonitor().beforeInvoke(createIdentification(invoker, invocation, true));
            String contextDataStr = contextData.toString();

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
                rpcContext.getAttachments().put("contextData", contextDataStr);
            } else {
                fix283SendNoAttachmentIssue(invocation, contextDataStr);
            }
        } else {
            // 读取参数
            String contextDataStr;

            if (!BugFixAcitve.isActive) {
                contextDataStr = rpcContext.getAttachment("contextData");
            } else {
                contextDataStr = fix283RecvNoAttachmentIssue(invocation);
            }

            ContextData contextData = null;
            if (contextDataStr != null && contextDataStr.length() > 0) {
                contextData = new ContextData(contextDataStr);
            }

            new RPCServerInvokeMonitor().beforeInvoke(contextData, createIdentification(invoker, invocation, false));
        }

    }

    @Override
    public Object afterMethod(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext,
            Object ret) {
        Result result = (Result) ret;
        if (result != null && result.getException() != null) {
            dealException(result.getException(), context);
        }

        if (isConsumer(context)) {
            new RPCClientInvokeMonitor().afterInvoke();
        } else {
            new RPCServerInvokeMonitor().afterInvoke();
        }

        return ret;
    }

    @Override
    public void handleMethodException(Throwable t, EnhancedClassInstanceContext context,
            InstanceMethodInvokeContext interceptorContext) {
        dealException(t, context);
    }


    private boolean isConsumer(EnhancedClassInstanceContext context) {
        return (boolean) context.get("isConsumer");
    }

    private void dealException(Throwable t, EnhancedClassInstanceContext context) {
        if (isConsumer(context)) {
            new RPCClientInvokeMonitor().occurException(t);
        } else {
            new RPCServerInvokeMonitor().occurException(t);
        }
    }

    private static Identification createIdentification(Invoker<?> invoker, Invocation invocation, boolean isConsumer) {
        StringBuilder viewPoint = new StringBuilder();
        if (isConsumer) {
            viewPoint.append("comsumer:");
        } else {
            viewPoint.append("provider:");
        }
        viewPoint.append(invoker.getUrl().getProtocol() + "://");
        viewPoint.append(invoker.getUrl().getHost());
        viewPoint.append(":" + invoker.getUrl().getPort());
        viewPoint.append(invoker.getUrl().getAbsolutePath());
        viewPoint.append("." + invocation.getMethodName() + "(");
        for (Class<?> classes : invocation.getParameterTypes()) {
            viewPoint.append(classes.getSimpleName() + ",");
        }

        if (invocation.getParameterTypes().length > 0) {
            viewPoint.delete(viewPoint.length() - 1, viewPoint.length());
        }

        viewPoint.append(")");
        return Identification.newBuilder().viewPoint(viewPoint.toString()).spanType(DubboBuriedPointType.instance())
                .build();
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
}
