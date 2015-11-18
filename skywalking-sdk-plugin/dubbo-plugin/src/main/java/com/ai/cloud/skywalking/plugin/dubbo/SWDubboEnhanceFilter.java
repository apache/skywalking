package com.ai.cloud.skywalking.plugin.dubbo;

import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointReceiver;
import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointSender;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.*;

@Activate
public class SWDubboEnhanceFilter implements Filter {

    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcContext context = RpcContext.getContext();
        boolean isConsumer = context.isConsumerSide();
        Result result = null;
        if (isConsumer) {
            RPCBuriedPointSender sender = new RPCBuriedPointSender();
            ContextData contextData = sender.beforeSend(createIdentification(invoker, invocation));
            // 追加参数
            RpcInvocation rpcInvocation = (RpcInvocation) invocation;
            rpcInvocation.setAttachment("contextData", contextData.toString());
            try {
                //执行结果
                result = invoker.invoke(invocation);
                //结果是否包含异常
                if (result.getException() != null) {
                    sender.handleException(result.getException());
                }
            } catch (RpcException e) {
                // 自身异常
                sender.handleException(e);
                throw e;
            } finally {
                sender.afterSend();
            }
        } else {
            // 读取参数
            RPCBuriedPointReceiver rpcBuriedPointReceiver = new RPCBuriedPointReceiver();
            RpcInvocation rpcInvocation = (RpcInvocation) invocation;
            String contextDataStr = rpcInvocation.getAttachment("contextData");
            ContextData contextData = null;
            if (contextDataStr != null && contextDataStr.length() > 0) {
                contextData = new ContextData(contextDataStr);
            }

            rpcBuriedPointReceiver.beforeReceived(contextData, createIdentification(invoker, invocation));

            try {
                //执行结果
                result = invoker.invoke(invocation);
                //结果是否包含异常
                if (result.getException() != null) {
                    rpcBuriedPointReceiver.handleException(result.getException());
                }
            } catch (RpcException e) {
                // 自身异常
                rpcBuriedPointReceiver.handleException(e);
                throw e;
            } finally {
                rpcBuriedPointReceiver.afterReceived();
            }
        }

        return result;
    }

    private static Identification createIdentification(Invoker<?> invoker, Invocation invocation) {
        StringBuilder viewPoint = new StringBuilder();
        viewPoint.append(invoker.getUrl().getProtocol() + "://");
        viewPoint.append(invoker.getUrl().getHost());
        viewPoint.append(":" + invoker.getUrl().getPort());
        viewPoint.append(invoker.getUrl().getAbsolutePath());
        viewPoint.append(invocation.getMethodName() + "(");
        for (Class classes : invocation.getParameterTypes()) {
            viewPoint.append(classes.getSimpleName() + ",");
        }

        if (invocation.getParameterTypes().length > 0) {
            viewPoint.delete(viewPoint.length() - 1, viewPoint.length());
        }

        viewPoint.append(")");
        return Identification.newBuilder().viewPoint(viewPoint.toString()).spanType('D').build();
    }
}
