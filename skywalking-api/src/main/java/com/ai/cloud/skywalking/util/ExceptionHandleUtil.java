package com.ai.cloud.skywalking.util;

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
        while (causeException.getCause() != null || expMessage.length() < MAX_EXCEPTION_STACK_LENGTH) {
            causeException.printStackTrace(new java.io.PrintWriter(buf, true));
            expMessage.append(buf.toString());
            causeException = causeException.getCause();
        }
        try {
            buf.close();
        } catch (IOException e1) {
            expMessage.append("\n本地发送埋点关闭异常读入流异常，异常信息:");
            expMessage.append(e1.getCause().getMessage());
        }
        if (expMessage.length() <= MAX_EXCEPTION_STACK_LENGTH) {
            return expMessage.toString().replace('\n', '&');
        } else {
            return expMessage.toString().replace('\n', '&').substring(0, MAX_EXCEPTION_STACK_LENGTH);
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
