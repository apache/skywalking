package com.ai.cloud.skywalking.reciever.storage.chain.alarm;

import static com.ai.cloud.skywalking.reciever.conf.Config.Alarm.ALARM_EXCEPTION_STACK_LENGTH;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.protocol.Span;

public class ExceptionChecker extends AbstractSpanChecker {
	private static Logger logger = LogManager.getLogger(ExceptionChecker.class);

	@Override
	public void check(Span span) {
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

	private String generateAlarmKey(Span span) {
        return span.getUserId() + "-" + span.getApplicationId() + "-"
                + (System.currentTimeMillis() / (10000 * 6));
    }
    
}
