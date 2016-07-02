package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.tracer.LocalMethodTracer;
import com.ai.cloud.skywalking.model.Identification;
import org.aspectj.lang.ProceedingJoinPoint;

public class TracingAspect {

    public Object doTracing(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        LocalMethodTracer localMethodTracer = new LocalMethodTracer();
        try {
            localMethodTracer.traceBeforeInvoke(Identification.newBuilder().viewPoint(proceedingJoinPoint.getSignature().toString()).spanType(SpringBuriedPointType.instance()).build());
            return proceedingJoinPoint.proceed();
        } catch (Throwable e) {
            localMethodTracer.occurException(e);
            throw e;
        } finally {
            localMethodTracer.traceAfterInvoke();
        }
    }
}
