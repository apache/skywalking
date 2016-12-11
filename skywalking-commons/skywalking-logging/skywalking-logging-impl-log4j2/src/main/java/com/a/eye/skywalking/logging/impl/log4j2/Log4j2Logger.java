package com.a.eye.skywalking.logging.impl.log4j2;

import com.a.eye.skywalking.logging.api.ILog;
import org.apache.logging.log4j.Logger;

/**
 * Created by wusheng on 2016/11/11.
 */
public class Log4j2Logger implements ILog {
    private Logger logger;

    public Log4j2Logger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void info(String message, Object... arguments) {
        logger.info(message, arguments);
    }

    @Override
    public void warn(String format, Object... arguments) {
        logger.warn(format, arguments);
    }

    @Override
    public void warn(String format, Object arguments, Throwable e) {
        logger.warn(format, arguments, e);
    }

    @Override
    public void error(String message, Throwable e) {
        logger.error(message, e);
    }

    @Override
    public void error(String message, Object argument, Throwable e) {
        logger.error(message, argument, e);
    }

    @Override
    public boolean isDebugEnable() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnable() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isWarnEnable() {
        return logger.isWarnEnabled();
    }

    @Override
    public boolean isErrorEnable() {
        return logger.isErrorEnabled();
    }

    @Override
    public void debug(String format) {
        logger.debug(format);
    }

    @Override
    public void debug(String format, Object... arguments) {
        logger.debug(format, arguments);
    }

    @Override
    public void error(String format) {
        logger.error(format);
    }
}
