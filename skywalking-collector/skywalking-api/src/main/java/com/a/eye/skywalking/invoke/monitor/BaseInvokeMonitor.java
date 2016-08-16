package com.a.eye.skywalking.invoke.monitor;

import com.a.eye.skywalking.buffer.ContextBuffer;
import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.Logger;
import com.a.eye.skywalking.conf.AuthDesc;
import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.protocol.AckSpan;
import com.a.eye.skywalking.protocol.RequestSpan;
import com.a.eye.skywalking.protocol.Span;
import com.a.eye.skywalking.protocol.util.BuriedPointMachineUtil;

import java.util.HashSet;
import java.util.Set;

public abstract class BaseInvokeMonitor {

    private static Logger logger = LogManager.getLogger(BaseInvokeMonitor.class);

    private static String EXCEPTION_SPLIT = ",";

    private static Set<String> exclusiveExceptionSet = null;

    protected ContextData beforeInvoke(Span spanData, Identification id) {
        if (Config.BuriedPoint.PRINTF) {
            logger.debug("TraceId:" + spanData.getTraceId() + "\tParentLevelId:" + spanData.getParentLevel()
                    + "\tLevelId:" + spanData.getLevelId() + "\tbusinessKey:" + spanData.getBusinessKey());
        }

        // 将新创建的Context存放到ThreadLocal栈中。
        CurrentThreadSpanStack.push(spanData);

        // 根据SpanData生成RequestSpan，并保存
        ContextBuffer.save(RequestSpan.RequestSpanBuilder.
                newBuilder(CurrentThreadSpanStack.peek()).callType(id.getCallType()).viewPoint(id.getViewPoint())
                .spanTypeDesc(id.getSpanTypeDesc()).processNo(BuriedPointMachineUtil.getProcessNo())
                .address(BuriedPointMachineUtil.getHostDesc()).build());

        // 并将当前的Context返回回去
        return new ContextData(spanData);
    }

    protected void afterInvoke() {
        try {
            if (!AuthDesc.isAuth())
                return;

            // 弹出上下文的栈顶中的元素
            Span spanData = CurrentThreadSpanStack.pop();

            if (Config.BuriedPoint.PRINTF) {
                logger.debug("TraceId-ACK:" + spanData.getTraceId() + "\tParentLevelId:" + spanData.getParentLevel()
                        + "\tLevelId:" + spanData.getLevelId() + "\tbusinessKey:" + spanData.getBusinessKey());
            }
            // 生成并保存到缓存
            ContextBuffer.save(new AckSpan(spanData));
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
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
            logger.error(t.getMessage(), t);
        }
    }
}
