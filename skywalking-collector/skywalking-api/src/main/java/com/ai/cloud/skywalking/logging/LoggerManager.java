package com.ai.cloud.skywalking.logging;

/**
 * Created by xin on 16-6-23.
 */
public class LoggerManager {

    public static Logger getLog(Class toBeClass) {
        return new Logger(toBeClass);
    }
}
