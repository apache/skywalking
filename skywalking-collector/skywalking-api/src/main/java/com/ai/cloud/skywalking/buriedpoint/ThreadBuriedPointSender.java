package com.ai.cloud.skywalking.buriedpoint;

import com.ai.cloud.skywalking.api.IBuriedPointSender;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.EmptyContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.util.BuriedPointMachineUtil;
import com.ai.cloud.skywalking.util.TraceIdGenerator;

/**
 * 暂不确定多线程的实现方式
 *
 * @author wusheng
 */
@Deprecated
public class ThreadBuriedPointSender extends BuriedPointInvoker
        implements IBuriedPointSender {
    private static Logger logger = LogManager
            .getLogger(ThreadBuriedPointSender.class);

    private Span span;

    public ThreadBuriedPointSender(int threadSeqId) {
        if (!AuthDesc.isAuth())
            return;
        Span spanData;
        // 从ThreadLocal中取出上下文
        final Span parentSpanData = Context.getLastSpan();
        if (parentSpanData == null) {
            spanData = new Span(TraceIdGenerator.generate(),
                    Config.SkyWalking.APPLICATION_CODE,
                    Config.SkyWalking.USER_ID);
        } else {
            // 如果不为空，则将当前的Context存放到上下文
            spanData = new Span(parentSpanData.getTraceId(),
                    Config.SkyWalking.APPLICATION_CODE,
                    Config.SkyWalking.USER_ID);
            spanData.setParentLevel(parentSpanData.getParentLevel() + "."
                    + parentSpanData.getLevelId());
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
        super.afterInvoker();
    }

}
