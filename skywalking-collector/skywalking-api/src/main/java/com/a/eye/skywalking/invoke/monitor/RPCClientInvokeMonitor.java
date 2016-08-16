package com.a.eye.skywalking.invoke.monitor;

import com.a.eye.skywalking.buffer.ContextBuffer;
import com.a.eye.skywalking.conf.AuthDesc;
import com.a.eye.skywalking.conf.Config;
import com.a.eye.skywalking.context.CurrentThreadSpanStack;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.Logger;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.EmptyContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.protocol.util.BuriedPointMachineUtil;
import com.a.eye.skywalking.protocol.util.ContextGenerator;
import com.a.eye.skywalking.protocol.RequestSpan;
import com.a.eye.skywalking.protocol.Span;
import com.a.eye.skywalking.protocol.common.SpanType;

public class RPCClientInvokeMonitor extends BaseInvokeMonitor {

    private static Logger logger = LogManager
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

            RequestSpan requestSpan = RequestSpan.RequestSpanBuilder.newBuilder(CurrentThreadSpanStack.peek())
                    .viewPoint(id.getViewPoint())
                    .spanTypeDesc(id.getSpanTypeDesc())
                    .bussinessKey(id.getBusinessKey())
                    .callType(id.getCallType()).processNo(BuriedPointMachineUtil.getProcessNo())
                    .address(BuriedPointMachineUtil.getHostDesc()).build();

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
