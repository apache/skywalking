package com.a.eye.skywalking.invoke.monitor;

import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.Logger;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.protocol.util.ContextGenerator;
import com.a.eye.skywalking.conf.AuthDesc;
import com.a.eye.skywalking.model.Span;
import com.a.eye.skywalking.protocol.common.SpanType;

public class RPCServerInvokeMonitor extends BaseInvokeMonitor {

    private static Logger logger = LogManager
            .getLogger(RPCServerInvokeMonitor.class);

    public void beforeInvoke(ContextData context, Identification id) {
        try {
            if (!AuthDesc.isAuth())
                return;

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
