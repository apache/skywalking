package com.a.eye.skywalking.invoke.monitor;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.model.Span;
import com.a.eye.skywalking.model.SpanType;
import com.a.eye.skywalking.protocol.util.ContextGenerator;

public class RPCServerInvokeMonitor extends BaseInvokeMonitor {

    private static ILog logger = LogManager
            .getLogger(RPCServerInvokeMonitor.class);

    public void beforeInvoke(ContextData context, Identification id) {
        try {
            Span spanData = ContextGenerator.generateSpanFromContextData(
                    context, id);
            // 设置是否为接收端
            spanData.setSpanType(SpanType.RPC_SERVER);

            super.beforeInvoke(spanData, id);
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }

    public void afterInvoke() {
        super.afterInvoke();
    }


    public void occurException(Throwable th) {
        super.occurException(th);
    }

}
