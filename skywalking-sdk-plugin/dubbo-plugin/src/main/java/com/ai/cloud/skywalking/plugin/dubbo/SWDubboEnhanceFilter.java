package com.ai.cloud.skywalking.plugin.dubbo;

import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointReceiver;
import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointSender;
import com.ai.cloud.skywalking.context.Span;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.alibaba.dubbo.rpc.*;

public class SWDubboEnhanceFilter implements Filter {

    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcContext context = RpcContext.getContext();
        boolean isConsumer = context.isConsumerSide();
        Result result = null;
        if (isConsumer) {
            RPCBuriedPointSender sender = new RPCBuriedPointSender();
            ContextData contextData = sender.beforeSend(createIdentification(invoker));
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
                // 反序列化参数
                String[] value = contextDataStr.split("-");
                Span span = new Span(value[0]);
                span.setParentLevel(value[1]);
                span.setLevelId(Integer.valueOf(value[2]));
                span.setSpanType(value[3].charAt(0));
                contextData = new ContextData(span);
            }

            rpcBuriedPointReceiver.beforeReceived(contextData, createIdentification(invoker));

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

    private static Identification createIdentification(Invoker<?> invoker) {
        StringBuilder businessKey = new StringBuilder();
        businessKey.append("IP:" + invoker.getUrl().getAddress());
        businessKey.append("Host:" + invoker.getUrl().getHost());
        businessKey.append("Port:" + invoker.getUrl().getPort());
        businessKey.append("Protocol:" + invoker.getUrl().getProtocol());
        return Identification.newBuilder().viewPoint(invoker.getUrl().getServiceInterface()).businessKey(businessKey.
                toString()).spanType('D').build();
    }
}
