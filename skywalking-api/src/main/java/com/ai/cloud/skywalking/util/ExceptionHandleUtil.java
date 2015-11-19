package com.ai.cloud.skywalking.util;

import com.ai.cloud.skywalking.constants.Constants;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.context.Span;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.ai.cloud.skywalking.conf.Config.BuriedPoint.MAX_EXCEPTION_STACK_LENGTH;

public final class ExceptionHandleUtil {
    private static String extractExceptionStackMessage(final Throwable e) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        StringBuilder expMessage = new StringBuilder();
        Throwable causeException = e;
        while (causeException != null && (causeException.getCause() != null || expMessage.length() < MAX_EXCEPTION_STACK_LENGTH)) {
            causeException.printStackTrace(new java.io.PrintWriter(buf, true));
            expMessage.append(buf.toString());
            causeException = causeException.getCause();
        }
        try {
            buf.close();
        } catch (IOException e1) {
            expMessage.append("\nClose exception stack input stream failed:\n");
            expMessage.append(e1.getCause().getMessage());
        }
        if (expMessage.length() <= MAX_EXCEPTION_STACK_LENGTH) {
            return expMessage.toString().replaceAll(Constants.NEW_LINE_CHARACTER_PATTERN, Constants.EXCEPTION_SPILT_PATTERN);
        } else {
            return expMessage.toString().replaceAll(Constants.NEW_LINE_CHARACTER_PATTERN, Constants.EXCEPTION_SPILT_PATTERN)
                    .substring(0, MAX_EXCEPTION_STACK_LENGTH);
        }
    }

    public static void handleException(Throwable e) {
        Span spanData = Context.getLastSpan();
        // 设置错误信息
        byte errorCode = 1;
        spanData.setStatusCode(errorCode);
        spanData.setExceptionStack(extractExceptionStackMessage(e));
    }
}
