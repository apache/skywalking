package com.ai.cloud.skywalking.plugin.spring;

import com.ai.cloud.skywalking.buriedpoint.LocalBuriedPointSender;
import com.ai.cloud.skywalking.buriedpoint.type.SpringBuriedPointType;
import com.ai.cloud.skywalking.model.Identification;
import org.aspectj.lang.ProceedingJoinPoint;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TracingAspect {

    public Object doTracing(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        LocalBuriedPointSender _sender = new LocalBuriedPointSender();
        try {
            StringBuilder viewPoint = new StringBuilder();
            viewPoint.append(proceedingJoinPoint.getTarget().getClass().getName() + "." + proceedingJoinPoint.getSignature().getName() + "(");
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
            _sender.beforeSend(Identification.newBuilder().viewPoint(viewPoint.toString()).spanType(SpringBuriedPointType.instance()).build());
            return proceedingJoinPoint.proceed();
        } catch (Throwable e) {
            _sender.handleException(e);
            throw e;
        } finally {
            _sender.afterSend();
        }
    }

    public static void main(String[] args) {
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(1451460306723L)));

    }
}
