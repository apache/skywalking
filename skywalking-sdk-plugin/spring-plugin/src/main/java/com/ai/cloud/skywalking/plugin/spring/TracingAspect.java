package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.buriedpoint.LocalBuriedPointSender;
import com.ai.cloud.skywalking.model.Identification;
import org.aspectj.lang.ProceedingJoinPoint;

public class TracingAspect {

    public Object doTracing(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        LocalBuriedPointSender _sender = new LocalBuriedPointSender();
        try {
            _sender.beforeSend(Identification.newBuilder().viewPoint(proceedingJoinPoint.getSignature().toString()).spanType(SpringBuriedPointType.instance()).build());
            return proceedingJoinPoint.proceed();
        } catch (Throwable e) {
            _sender.handleException(e);
            throw e;
        } finally {
            _sender.afterSend();
        }
    }
}
