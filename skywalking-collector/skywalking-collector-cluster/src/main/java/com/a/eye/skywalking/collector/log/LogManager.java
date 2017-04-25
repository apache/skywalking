package com.a.eye.skywalking.collector.log;

import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public enum LogManager {
    INSTANCE;

    public Logger getFormatterLogger(final Class<?> clazz) {
        return org.apache.logging.log4j.LogManager.getFormatterLogger(clazz);
    }
}
