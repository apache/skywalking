package com.ai.cloud.skywalking.tracer;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.context.CurrentThreadSpanStack;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.protocol.SpanType;
import com.ai.cloud.skywalking.util.ContextGenerator;

public class RPCServerTracer extends BaseTracer{

    private static Logger logger = LogManager
            .getLogger(RPCServerTracer.class);

    public void traceBeforeInvoke(ContextData context, Identification id) {
        try {
            if (!AuthDesc.isAuth())
                return;

            Span spanData = ContextGenerator.generateSpanFromContextData(
                    context, id);
            // 设置是否为接收端
            spanData.setSpanType(SpanType.RPC_SERVER);

            invalidateAllSpanIfIsNotFirstSpan(spanData);

            super.traceBeforeInvoke(spanData);
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }

    public void traceAfterInvoke(){
        super.traceAfterInvoke();
    }


    public void occurException(Throwable th){
        super.occurException(th);
    }

    private void invalidateAllSpanIfIsNotFirstSpan(Span spanData) {
        if (!CurrentThreadSpanStack.peek().getTraceId().equals(spanData.getTraceId())) {
            CurrentThreadSpanStack.invalidatePresentSpans();
        }
    }
}
