package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.invoke.monitor.LocalMethodInvokeMonitor;
import com.ai.cloud.skywalking.model.Identification;
import org.aspectj.lang.ProceedingJoinPoint;

public class TracingAspect {

    public Object doTracing(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        LocalMethodInvokeMonitor localMethodInvokeMonitor = new LocalMethodInvokeMonitor();
        try {
            localMethodInvokeMonitor.beforeInvoke(Identification.newBuilder().viewPoint(proceedingJoinPoint.getSignature().toString()).spanType(SpringBuriedPointType.instance()).build());
            return proceedingJoinPoint.proceed();
        } catch (Throwable e) {
            localMethodInvokeMonitor.occurException(e);
            throw e;
        } finally {
            localMethodInvokeMonitor.afterInvoke();
        }
    }
}
