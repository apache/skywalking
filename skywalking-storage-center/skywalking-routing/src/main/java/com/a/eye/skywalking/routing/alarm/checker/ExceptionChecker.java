package com.a.eye.skywalking.routing.alarm.checker;

import com.a.eye.skywalking.routing.config.Config;
import com.a.eye.skywalking.routing.disruptor.ack.AckSpanHolder;

public class ExceptionChecker implements ISpanChecker {

    @Override
    public CheckResult check(AckSpanHolder span) {
        if (span.getStatusCode() != 1)
            return new CheckResult();
        String exceptionStack = span.getExceptionStack();
        if (exceptionStack == null) {
            exceptionStack = "";
        } else if (exceptionStack.length() > Config.Alarm.ALARM_EXCEPTION_STACK_LENGTH) {
            exceptionStack = exceptionStack.substring(0, Config.Alarm.ALARM_EXCEPTION_STACK_LENGTH);
        }

        return new CheckResult(FatalReason.EXCEPTION_ERROR, exceptionStack);
    }

}
