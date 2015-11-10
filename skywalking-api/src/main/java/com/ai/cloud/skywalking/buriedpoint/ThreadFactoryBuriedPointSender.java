package com.ai.cloud.skywalking.buriedpoint;

import com.ai.cloud.skywalking.api.IBuriedPointSender;
import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.context.Span;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.util.ContextGenerator;
import com.ai.cloud.skywalking.util.ExceptionHandleUtil;

public class ThreadFactoryBuriedPointSender implements IBuriedPointSender {

    public ContextData beforeSend(Identification id) {
        Span spanData = ContextGenerator.generateContextFromThreadLocal(id);
        // 3.将新创建的Context存放到ThreadLocal栈中。
        Context.getOrCreate().append(spanData);
        // 4 并将当前的Context返回回去
        return new ContextData(spanData);
    }

    public void afterSend() {
        // 获取上下文的栈顶中的元素
        Span spanData = Context.getOrCreate().removeLastSpan();
        if (spanData == null) {
            return;
        }
        // 填上必要信息
        spanData.setCost(System.currentTimeMillis() - spanData.getStartDate());
        // 存放到本地发送进程中
        ContextBuffer.save(spanData);
    }

    public void handleException(Throwable th) {
        ExceptionHandleUtil.handleException(th);
    }
}
