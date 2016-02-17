package com.ai.cloud.skywalking.buriedpoint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.api.IBuriedPointSender;
import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.EmptyContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.util.BuriedPointMachineUtil;
import com.ai.cloud.skywalking.util.TraceIdGenerator;


public class ThreadBuriedPointSender extends ApplicationExceptionHandler implements IBuriedPointSender {
	private static Logger logger = LogManager.getLogger(ThreadBuriedPointSender.class);

    private Span span;

    public ThreadBuriedPointSender(int threadSeqId) {
        if (!AuthDesc.isAuth())
            return;
        Span spanData;
        // 从ThreadLocal中取出上下文
        final Span parentSpanData = Context.getLastSpan();
        if (parentSpanData == null) {
            spanData = new Span(TraceIdGenerator.generate(), Config.SkyWalking.APPLICATION_CODE,
                    Config.SkyWalking.USER_ID);
        } else {
            // 如果不为空，则将当前的Context存放到上下文
            spanData = new Span(parentSpanData.getTraceId(), Config.SkyWalking.APPLICATION_CODE,
                    Config.SkyWalking.USER_ID);
            spanData.setParentLevel(parentSpanData.getParentLevel() + "." + parentSpanData.getLevelId());
            spanData.setLevelId(threadSeqId);
        }
        this.span = spanData;
    }

    public ContextData beforeSend(Identification id) {
        if (!AuthDesc.isAuth())
            return new EmptyContextData();

        if (this.span == null) {
            return null;
        }
        span.setStartDate(System.currentTimeMillis());
        span.setViewPointId(id.getViewPoint());
        span.setProcessNo(BuriedPointMachineUtil.getProcessNo());
        Context.append(span);
        return new ContextData(span);
    }

    public void afterSend() {
        Span span = Context.removeLastSpan();
        if (span == null) {
            return;
        }
        // 填上必要信息
        span.setCost(System.currentTimeMillis() - span.getStartDate());
        if (Config.BuriedPoint.PRINTF) {
            logger.debug("viewpointId:" + span.getViewPointId() + "\tParentLevelId:" + span.
                    getParentLevel() + "\tLevelId:" + span.getLevelId());
        }
        // 存放到本地发送进程中
        if (!Config.Sender.IS_OFF) {
            ContextBuffer.save(span);
        }
    }

}
