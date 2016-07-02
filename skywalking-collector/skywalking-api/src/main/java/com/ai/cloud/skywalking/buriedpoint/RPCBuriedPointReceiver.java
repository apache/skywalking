package com.ai.cloud.skywalking.buriedpoint;

import com.ai.cloud.skywalking.api.IBuriedPointReceiver;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.protocol.SpanType;
import com.ai.cloud.skywalking.util.ContextGenerator;

public class RPCBuriedPointReceiver extends BuriedPointInvoker
        implements IBuriedPointReceiver {

    private static Logger logger = LogManager
            .getLogger(RPCBuriedPointReceiver.class);

    public void afterReceived() {
        super.afterInvoker();
    }

    public void beforeReceived(ContextData context, Identification id) {
        try {
            if (!AuthDesc.isAuth())
                return;

            Span spanData = ContextGenerator.generateSpanFromContextData(
                    context, id);
            // 设置是否为接收端
            spanData.setSpanType(SpanType.RPC_SERVER);

            invalidateAllSpanIfIsNotFirstSpan(spanData);

            super.beforeInvoker(spanData);
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }

    private void invalidateAllSpanIfIsNotFirstSpan(Span spanData) {
        if (!Context.getLastSpan().getTraceId().equals(spanData.getTraceId())) {
            Context.invalidateAllSpan();
        }
    }
}
