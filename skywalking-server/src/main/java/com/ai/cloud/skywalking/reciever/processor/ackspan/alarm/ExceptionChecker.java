package com.ai.cloud.skywalking.reciever.processor.ackspan.alarm;

import com.ai.cloud.skywalking.protocol.AckSpan;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.ai.cloud.skywalking.reciever.conf.Config.Alarm.ALARM_EXCEPTION_STACK_LENGTH;

public class ExceptionChecker extends AbstractSpanChecker {
    private static Logger logger = LogManager.getLogger(ExceptionChecker.class);

    @Override
    public void check(AckSpan span) {
        if (span.getStatusCode() != 1)
            return;
        String exceptionStack = span.getExceptionStack();
        if (exceptionStack == null) {
            exceptionStack = "";
        } else if (exceptionStack.length() > ALARM_EXCEPTION_STACK_LENGTH) {
            exceptionStack = exceptionStack.substring(0, ALARM_EXCEPTION_STACK_LENGTH);
        }
        saveAlarmMessage(generateAlarmKey(span), span.getTraceId(), exceptionStack);
    }

    private String generateAlarmKey(AckSpan span) {
        return span.getUserId() + "-" + span.getApplicationId() + "-" + (System.currentTimeMillis() / (10000 * 6));
    }

}
