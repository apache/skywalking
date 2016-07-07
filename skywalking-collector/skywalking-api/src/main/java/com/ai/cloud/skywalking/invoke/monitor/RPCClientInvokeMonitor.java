package com.ai.cloud.skywalking.invoke.monitor;

import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.context.CurrentThreadSpanStack;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.EmptyContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.RequestSpan;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.protocol.common.SpanType;
import com.ai.cloud.skywalking.protocol.util.ContextGenerator;

public class RPCClientInvokeMonitor extends BaseInvokeMonitor {

    private static Logger logger = LogManager
            .getLogger(RPCClientInvokeMonitor.class);

    public ContextData traceBeforeInvoke(Identification id) {
        try {
            if (!AuthDesc.isAuth())
                return new EmptyContextData();

            Span spanData = ContextGenerator.generateSpanFromThreadLocal(id);
            //设置SpanType的类型
            spanData.setSpanType(SpanType.RPC_CLIENT);

            ContextBuffer.save(new RequestSpan(spanData));
            CurrentThreadSpanStack.push(spanData);

            return new ContextData(spanData.getTraceId(), generateSubParentLevelId(spanData));
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            return new EmptyContextData();
        }
    }

    public void afterInvoke(){
        super.afterInvoke();
    }


    public void occurException(Throwable th){
        super.occurException(th);
    }

    private String generateSubParentLevelId(Span spanData) {
        if (spanData.getParentLevel() == null) {
            return spanData.getLevelId() + "";
        }

        return spanData.getParentLevel() + "." + spanData.getLevelId();
    }

}
