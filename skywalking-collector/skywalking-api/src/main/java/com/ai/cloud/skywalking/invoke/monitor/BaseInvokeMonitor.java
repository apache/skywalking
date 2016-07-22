package com.ai.cloud.skywalking.invoke.monitor;

import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.CurrentThreadSpanStack;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.AckSpan;
import com.ai.cloud.skywalking.protocol.RequestSpan;
import com.ai.cloud.skywalking.protocol.Span;

import java.util.HashSet;
import java.util.Set;

import static com.ai.cloud.skywalking.conf.Config.BuriedPoint.EXCLUSIVE_EXCEPTIONS;

public abstract class BaseInvokeMonitor {

    private static Logger logger = LogManager.getLogger(BaseInvokeMonitor.class);

    private static String EXCEPTION_SPLIT = ",";

    private static Set<String> exclusiveExceptionSet = null;

    protected ContextData beforeInvoke(Span spanData, Identification id) {
        if (Config.BuriedPoint.PRINTF) {
            logger.debug("TraceId:" + spanData.getTraceId() + "\tviewpointId:" + id.getViewPoint() + "\tParentLevelId:"
                    + spanData.getParentLevel() + "\tLevelId:" + spanData.getLevelId());
        }


        // 根据SpanData生成RequestSpan，并保存
        ContextBuffer.save(RequestSpan.RequestSpanBuilder.
                newBuilder(spanData).callType(id.getCallType()).viewPoint(id.getViewPoint())
                .spanTypeDesc(id.getSpanTypeDesc()).build());

        // 将新创建的Context存放到ThreadLocal栈中。
        CurrentThreadSpanStack.push(spanData);
        // 并将当前的Context返回回去
        return new ContextData(spanData);
    }

    protected void afterInvoke() {
        afterInvoke(null);
    }

    protected void afterInvoke(String invokeResult) {
        try {
            if (!AuthDesc.isAuth())
                return;

            // 弹出上下文的栈顶中的元素
            Span spanData = CurrentThreadSpanStack.pop();
            spanData.setInvokeResult(invokeResult);

            if (Config.BuriedPoint.PRINTF) {
                logger.debug("TraceId-ACK:" + spanData.getTraceId() + "\tParentLevelId:" + spanData.getParentLevel()
                        + "\tLevelId:" + spanData.getLevelId() + "\tbusinessKey:" + spanData.getParameters());
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

                String[] exceptions = EXCLUSIVE_EXCEPTIONS.split(EXCEPTION_SPLIT);
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
