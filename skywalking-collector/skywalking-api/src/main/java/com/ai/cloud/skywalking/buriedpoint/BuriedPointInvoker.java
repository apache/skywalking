package com.ai.cloud.skywalking.buriedpoint;

import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.protocol.Span;

import java.util.HashSet;
import java.util.Set;

import static com.ai.cloud.skywalking.conf.Config.BuriedPoint.EXCLUSIVE_EXCEPTIONS;

public class BuriedPointInvoker {

    private static Logger logger = LogManager
            .getLogger(BuriedPointInvoker.class);

    private static String EXCEPTION_SPLIT = ",";

    private static Set<String> exclusiveExceptionSet = null;


    public ContextData beforeInvoker(Span spanData) {
        if (Config.BuriedPoint.PRINTF) {
            logger.debug("TraceId:" + spanData.getTraceId()
                    + "\tviewpointId:" + spanData.getViewPointId()
                    + "\tParentLevelId:" + spanData.getParentLevel()
                    + "\tLevelId:" + spanData.getLevelId());
        }

        // 将新创建的Context存放到ThreadLocal栈中。
        Context.append(spanData);
        // 并将当前的Context返回回去
        return new ContextData(spanData);
    }

    public void afterInvoker() {
        try {
            if (!AuthDesc.isAuth())
                return;

            // 弹出上下文的栈顶中的元素
            Span spanData = Context.removeLastSpan();
            if (spanData == null || spanData.isInvalidate()) {
                return;
            }

            // 加上花费时间
            spanData.setCost(System.currentTimeMillis()
                    - spanData.getStartDate());

            if (Config.BuriedPoint.PRINTF) {
                logger.debug("TraceId:" + spanData.getTraceId()
                        + "\tviewpointId:" + spanData.getViewPointId()
                        + "\tParentLevelId:" + spanData.getParentLevel()
                        + "\tLevelId:" + spanData.getLevelId()
                        + "\tbusinessKey:" + spanData.getBusinessKey());
            }

            ContextBuffer.save(spanData);
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }

    public void handleException(Throwable th) {
        try {
            if (exclusiveExceptionSet == null) {
                Set<String> exclusiveExceptions = new HashSet<String>();

                String[] exceptions = EXCLUSIVE_EXCEPTIONS
                        .split(EXCEPTION_SPLIT);
                for (String exception : exceptions) {
                    exclusiveExceptions.add(exception);
                }
                exclusiveExceptionSet = exclusiveExceptions;
            }

            Span span = Context.getLastSpan();
            span.handleException(th, exclusiveExceptionSet,
                    Config.BuriedPoint.MAX_EXCEPTION_STACK_LENGTH);
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }
}
