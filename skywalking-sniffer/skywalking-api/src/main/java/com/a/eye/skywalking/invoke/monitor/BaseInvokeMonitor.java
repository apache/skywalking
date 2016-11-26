package com.a.eye.skywalking.invoke.monitor;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.disruptor.AckSpanDisruptor;
import com.a.eye.skywalking.disruptor.RequestSpanDisruptor;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.model.Span;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.protocol.util.BuriedPointMachineUtil;

import java.util.HashSet;
import java.util.Set;

public abstract class BaseInvokeMonitor {

    private static ILog easyLogger = LogManager.getLogger(BaseInvokeMonitor.class);

    private static String EXCEPTION_SPLIT = ",";

    private static Set<String> exclusiveExceptionSet = null;

    protected ContextData beforeInvoke(Span spanData, Identification id) {
        if (Config.BuriedPoint.PRINTF) {
            easyLogger.debug("TraceId:" + spanData.getTraceId() + "\tParentLevelId:" + spanData.getParentLevel()
                    + "\tLevelId:" + spanData.getLevelId() + "\tbusinessKey:" + spanData.getBusinessKey());
        }

        // 将新创建的Context存放到ThreadLocal栈中。
        CurrentThreadSpanStack.push(spanData);

        // 根据SpanData生成RequestSpan，并保存

        CurrentThreadSpanStack.push(spanData);

        sendRequestSpan(spanData, id);

        // 并将当前的Context返回回去
        return new ContextData(spanData);
    }

    protected void sendRequestSpan(Span span, Identification id){
        RequestSpan.Builder requestSpanBuilder = span.buildRequestSpan(RequestSpan.newBuilder());
        RequestSpan requestSpan = requestSpanBuilder
                .setViewPointId(id.getViewPoint())
                .setSpanTypeDesc(id.getSpanTypeDesc())
                .setBusinessKey(id.getBusinessKey())
                .setCallType(id.getCallType()).setProcessNo(BuriedPointMachineUtil.getProcessNo())
                .setAddress(BuriedPointMachineUtil.getHostDesc()).build();

        RequestSpanDisruptor.INSTANCE.ready2Send(requestSpan);
    }

    protected void sendAckSpan(Span span){
        AckSpan ackSpan = span.buildAckSpan(AckSpan.newBuilder()).build();

        AckSpanDisruptor.INSTANCE.ready2Send(ackSpan);
    }

    protected void afterInvoke() {
        try {

            // 弹出上下文的栈顶中的元素
            Span spanData = CurrentThreadSpanStack.pop();

            if (Config.BuriedPoint.PRINTF) {
                easyLogger.debug("TraceId-ACK:" + spanData.getTraceId() + "\tParentLevelId:" + spanData.getParentLevel()
                        + "\tLevelId:" + spanData.getLevelId() + "\tbusinessKey:" + spanData.getBusinessKey());
            }

            sendAckSpan(spanData);
        } catch (Throwable t) {
            easyLogger.error(t.getMessage(), t);
        }
    }

    protected void occurException(Throwable th) {
        try {
            if (exclusiveExceptionSet == null) {
                Set<String> exclusiveExceptions = new HashSet<String>();

                String[] exceptions = Config.BuriedPoint.EXCLUSIVE_EXCEPTIONS.split(EXCEPTION_SPLIT);
                for (String exception : exceptions) {
                    exclusiveExceptions.add(exception);
                }
                exclusiveExceptionSet = exclusiveExceptions;
            }

            Span span = CurrentThreadSpanStack.peek();
            span.handleException(th, exclusiveExceptionSet, Config.BuriedPoint.MAX_EXCEPTION_STACK_LENGTH);
        } catch (Throwable t) {
            easyLogger.error(t.getMessage(), t);
        }
    }
}
