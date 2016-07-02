package com.ai.cloud.skywalking.tracer;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.EmptyContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.util.ContextGenerator;

public class LocalMethodTracer extends BaseTracer {

    private static Logger logger = LogManager
            .getLogger(LocalMethodTracer.class);

    public ContextData traceBeforeInvoke(Identification id) {
        try {
            if (!AuthDesc.isAuth())
                return new EmptyContextData();

            Span spanData = ContextGenerator.generateSpanFromThreadLocal(id);

            return super.traceBeforeInvoke(spanData);
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            return new EmptyContextData();
        }
    }

    public void traceAfterInvoke(){
        super.traceAfterInvoke();
    }


    public void occurException(Throwable th){
        super.occurException(th);
    }

}
