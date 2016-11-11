package com.a.eye.skywalking.logging;

/**
 * Created by xin on 2016/11/10.
 */
public class LogManager {
    private static LogResolver resolver;

    public static void setLogResolver(LogResolver resolver) {
        LogManager.resolver = resolver;
    }

    public static ILog getLogger(Class<?> clazz) {
        if (resolver == null) {
            return NoopLogger.INSTANCE;
        }
        return LogManager.resolver.getLogger(clazz);
    }
}
