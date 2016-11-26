package com.a.eye.skywalking.invoke.monitor;

import com.a.eye.skywalking.buffer.ContextBuffer;
import com.a.eye.skywalking.conf.AuthDesc;
import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.model.*;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.protocol.util.BuriedPointMachineUtil;
import com.a.eye.skywalking.protocol.util.ContextGenerator;

public class RPCClientInvokeMonitor extends BaseInvokeMonitor {

    private static ILog logger = LogManager
            .getLogger(RPCClientInvokeMonitor.class);

    public ContextData beforeInvoke(Identification id) {
        try {
            if (!AuthDesc.isAuth())
                return new EmptyContextData();

            Span spanData = ContextGenerator.generateSpanFromThreadLocal(id);
            //设置SpanType的类型
            spanData.setSpanType(SpanType.RPC_CLIENT);

            if (Config.BuriedPoint.PRINTF) {
                logger.debug("TraceId:" + spanData.getTraceId() + "\tParentLevelId:" + spanData.getParentLevel()
                        + "\tLevelId:" + spanData.getLevelId() + "\tbusinessKey:" + spanData.getBusinessKey());
            }

            CurrentThreadSpanStack.push(spanData);

            Span span = CurrentThreadSpanStack.peek();
            RequestSpan.Builder requestSpanBuilder = span.buildRequestSpan(RequestSpan.newBuilder());
            RequestSpan requestSpan = requestSpanBuilder
                    .setViewPointId(id.getViewPoint())
                    .setSpanTypeDesc(id.getSpanTypeDesc())
                    .setBussinessKey(id.getBusinessKey())
                    .setCallType(id.getCallType()).setProcessNo(BuriedPointMachineUtil.getProcessNo())
                    .setAddress(BuriedPointMachineUtil.getHostDesc()).build();

            ContextBuffer.save(requestSpan);

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
        if (spanData.getParentLevel() == null || spanData.getParentLevel().length() == 0) {
            return spanData.getLevelId() + "";
        }

        return spanData.getParentLevel() + "." + spanData.getLevelId();
    }

}
