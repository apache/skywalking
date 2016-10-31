package com.a.eye.skywalking.logging;

/**
 * Created by xin on 16-6-23.
 */
public class LogManager {

    public static Logger getLogger(Class toBeLoggerClass) {
        return new Logger(toBeLoggerClass);
    }
}
