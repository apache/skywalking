package com.a.eye.skywalking.storage.alarm.checker;

import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.spandata.AckSpanData;

public class ExceptionChecker implements ISpanChecker {

    @Override
    public CheckResult check(AckSpanData span) {
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
