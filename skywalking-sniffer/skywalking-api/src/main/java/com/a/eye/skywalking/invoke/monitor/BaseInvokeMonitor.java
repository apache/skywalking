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
import com.a.eye.skywalking.model.SpanTagBuilder;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.util.BuriedPointMachineUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Basic invoke monitor.
 *
 * @author wusheng
 */
public abstract class BaseInvokeMonitor {

    private static ILog easyLogger = LogManager.getLogger(BaseInvokeMonitor.class);

    private static String EXCEPTION_SPLIT = ",";

    private static Set<String> exclusiveExceptionSet = null;

    /**
     * Create the request span before invoke method.
     *
     * @param spanData {@link Span} represents the before invoke.
     * @param id
     * @return
     */
    protected ContextData beforeInvoke(Span spanData, Identification id) {
        if (Config.BuriedPoint.PRINTF) {
            easyLogger
                    .debug("TraceId:" + spanData.getTraceId() + "\tParentLevelId:" + spanData.getParentLevel() + "\tLevelId:" + spanData.getLevelId() + "\tbusinessKey:" + spanData
                            .getBusinessKey());
        }

        // 将新创建的Context存放到ThreadLocal栈中。
        CurrentThreadSpanStack.push(spanData);

        sendRequestSpan(spanData, id);

        // 并将当前的Context返回回去
        return new ContextData(spanData);
    }

    /**
     * Change the {@link Span} to {@link RequestSpan}, and prepare to send to routing server.
     *
     * @param span
     * @param id
     */
    protected void sendRequestSpan(Span span, Identification id) {
        RequestSpan requestSpan = SpanTagBuilder.newBuilder(span).setBusinessKey(id.getBusinessKey()).setSpanTypeDesc(id.getSpanTypeDesc()).setCallType(id.getCallType())
                .setProcessNo(BuriedPointMachineUtil.getProcessNo()).setAddress(BuriedPointMachineUtil.getHostDesc()).buildRequestSpan(RequestSpan.newBuilder());

        RequestSpanDisruptor.INSTANCE.ready2Send(requestSpan);
    }

    /**
     * Change the {@link Span} to {@link AckSpan}, and prepare to send to routing server.
     *
     * @param span
     */
    protected void sendAckSpan(Span span) {
        AckSpan ackSpan = SpanTagBuilder.newBuilder(span).setStatusCode(span.getStatusCode()).setExceptionStack(span.getExceptionStack()).buildAckSpan(AckSpan.newBuilder());

        AckSpanDisruptor.INSTANCE.ready2Send(ackSpan);
    }

    /**
     * Create the ack span before invoke method.
     */
    protected void afterInvoke() {
        try {

            // 弹出上下文的栈顶中的元素
            Span spanData = CurrentThreadSpanStack.pop();

            if (Config.BuriedPoint.PRINTF) {
                easyLogger.debug("TraceId-ACK:" + spanData.getTraceId() + "\tParentLevelId:" + spanData.getParentLevel() + "\tLevelId:" + spanData.getLevelId() + "\tbusinessKey:"
                        + spanData.getBusinessKey());
            }

            sendAckSpan(spanData);
        } catch (Throwable t) {
            easyLogger.error(t.getMessage(), t);
        }
    }

    /**
     * Process when method invocation occurs exception.
     *
     * @param th
     */
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
