package com.a.eye.skywalking.invoke.monitor;

import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.model.*;
import com.a.eye.skywalking.util.ContextGenerator;

public class RPCClientInvokeMonitor extends BaseInvokeMonitor {

    private static ILog logger = LogManager
            .getLogger(RPCClientInvokeMonitor.class);

    public ContextData beforeInvoke(Identification id) {
        try {
            Span spanData = ContextGenerator.generateSpanFromThreadLocal(id);
            //设置SpanType的类型
            spanData.setSpanType(SpanType.RPC_CLIENT);

            if (Config.BuriedPoint.PRINTF) {
                logger.debug("TraceId:" + spanData.getTraceId() + "\tParentLevelId:" + spanData.getParentLevel()
                        + "\tLevelId:" + spanData.getLevelId() + "\tbusinessKey:" + spanData.getBusinessKey());
            }

            CurrentThreadSpanStack.push(spanData);

            sendRequestSpan(spanData, id);

            return new ContextData(spanData.getTraceId(), generateSubParentLevelId(spanData), spanData.getRouteKey());
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
        if (spanData.getParentLevel() == null || spanData.getParentLevel().length() == 0) {
            return spanData.getLevelId() + "";
        }

        return spanData.getParentLevel() + "." + spanData.getLevelId();
    }

}
