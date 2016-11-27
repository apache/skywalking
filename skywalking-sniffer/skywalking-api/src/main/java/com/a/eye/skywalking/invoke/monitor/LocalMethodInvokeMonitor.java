package com.a.eye.skywalking.invoke.monitor;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.EmptyContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.model.Span;
import com.a.eye.skywalking.util.ContextGenerator;

public class LocalMethodInvokeMonitor extends BaseInvokeMonitor {

    private static ILog logger = LogManager
            .getLogger(LocalMethodInvokeMonitor.class);

    public ContextData beforeInvoke(Identification id) {
        try {
            Span spanData = ContextGenerator.generateSpanFromThreadLocal(id);

            return super.beforeInvoke(spanData,id);
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

}
