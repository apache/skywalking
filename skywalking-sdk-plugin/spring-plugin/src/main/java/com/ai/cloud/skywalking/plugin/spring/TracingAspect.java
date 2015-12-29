package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.buriedpoint.LocalBuriedPointSender;
import com.ai.cloud.skywalking.model.Identification;
import org.aspectj.lang.ProceedingJoinPoint;

public class TracingAspect {

    public Object doTracing(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        LocalBuriedPointSender _sender = new LocalBuriedPointSender();
        try {
            StringBuilder viewPoint = new StringBuilder();
            viewPoint.append(proceedingJoinPoint.getTarget().getClass().getName()
                    + "(");
            boolean first = true;
            for (Object arg : proceedingJoinPoint.getArgs()) {
                if (!first) {
                    viewPoint.append(",");
                } else {
                    first = false;
                }
                viewPoint.append(arg.getClass().getName());
            }
            viewPoint.append(")");
            _sender.beforeSend(Identification.newBuilder().viewPoint(viewPoint.toString()).spanType("M").build());
            return proceedingJoinPoint.proceed();
        } catch (Throwable e) {
            _sender.handleException(e);
            throw e;
        } finally {
            _sender.afterSend();
        }
    }
}
