package com.ai.cloud.skywalking.buriedpoint;

import com.ai.cloud.skywalking.api.IBuriedPointSender;
import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.context.Span;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.SendData;
import com.ai.cloud.skywalking.util.BuriedPointMachineUtil;
import com.ai.cloud.skywalking.util.ExceptionHandleUtil;
import com.ai.cloud.skywalking.util.TraceIdGenerator;

public class ThreadBuriedPointSender implements IBuriedPointSender {

    private Span span;

    public ThreadBuriedPointSender(int threadSeqId) {
        Span spanData;
        // 从ThreadLocal中取出上下文
        final Span parentSpanData =  Context.getOrCreate().getLastSpan();
        if (parentSpanData == null) {
            spanData = new Span(TraceIdGenerator.generate());
        } else {
            // 如果不为空，则将当前的Context存放到上下文
            spanData = new Span(parentSpanData.getTraceId());
            spanData.setParentLevel(parentSpanData.getParentLevel() + "." + parentSpanData.getLevelId());
            spanData.setLevelId(threadSeqId);
        }
        this.span = spanData;
    }

    public ContextData beforeSend(SendData sendData) {
        if (this.span == null) {
            return null;
        }
        span.setStartDate(System.currentTimeMillis());
        span.setViewPointId(sendData.getViewPoint());
        span.setProcessNo(BuriedPointMachineUtil.getProcessNo());
        Context.getOrCreate().append(span);
        return new ContextData(span);
    }

    public void afterSend() {
        Span span =  Context.getOrCreate().removeLastSpan();
        if (span == null) {
            return;
        }
        // 填上必要信息
        span.setCost(System.currentTimeMillis() - span.getStartDate());
        // 存放到本地发送进程中
        ContextBuffer.save(span);
    }

    public void handleException(Throwable th) {
        ExceptionHandleUtil.handleException(th);
    }
}
