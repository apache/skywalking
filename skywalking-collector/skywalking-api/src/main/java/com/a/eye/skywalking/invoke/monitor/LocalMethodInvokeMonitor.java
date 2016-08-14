package com.a.eye.skywalking.invoke.monitor;

import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.Logger;
import com.a.eye.skywalking.model.ContextData;
import com.a.eye.skywalking.model.EmptyContextData;
import com.a.eye.skywalking.model.Identification;
import com.a.eye.skywalking.protocol.util.ContextGenerator;
import com.a.eye.skywalking.conf.AuthDesc;
import com.a.eye.skywalking.protocol.Span;

public class LocalMethodInvokeMonitor extends BaseInvokeMonitor {

    private static Logger logger = LogManager
            .getLogger(LocalMethodInvokeMonitor.class);

    public ContextData beforeInvoke(Identification id) {
        try {
            if (!AuthDesc.isAuth())
                return new EmptyContextData();

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

    public void afterInvoke(String invokeResult){
        super.afterInvoke(invokeResult);
    }


    public void occurException(Throwable th){
        super.occurException(th);
    }

}
