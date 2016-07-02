package com.ai.cloud.skywalking.buriedpoint;

import com.ai.cloud.skywalking.api.IBuriedPointSender;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.EmptyContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.util.ContextGenerator;

/**
 * 暂不确定多线程的实现方式
 *
 * @author wusheng
 */
@Deprecated
public class ThreadFactoryBuriedPointSender extends BuriedPointInvoker
        implements IBuriedPointSender {
    private static Logger logger = LogManager
            .getLogger(ThreadBuriedPointSender.class);

    public ContextData beforeSend(Identification id) {
        if (!AuthDesc.isAuth())
            return new EmptyContextData();

        Span spanData = ContextGenerator.generateSpanFromThreadLocal(id);
        // 将新创建的Context存放到ThreadLocal栈中。
        Context.append(spanData);
        // 并将当前的Context返回回去
        return new ContextData(spanData);
    }

    public void afterSend() {
        super.afterInvoker();
    }
}
