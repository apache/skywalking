package com.a.eye.skywalking.logging.log4j2;

import com.a.eye.skywalking.logging.ILog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author wusheng
 */
public class Log4j2Logger implements ILog {
    private Logger delegateLogger;

    Log4j2Logger(Class<?> targetClass){
        delegateLogger = LogManager.getFormatterLogger(targetClass);
    }

    @Override
    public void info(String format) {
        delegateLogger.info(format);
    }

    @Override
    public void info(String format, Object... arguments) {
        delegateLogger.info(format, arguments);
    }

    @Override
    public void warn(String format, Object... arguments) {
        delegateLogger.warn(format, arguments);
    }

    @Override
    public void error(String format, Throwable e) {
        delegateLogger.error(format, e);
    }

    @Override
    public void error(Throwable e, String format, Object... arguments) {
        delegateLogger.error(format, e, arguments);
    }

    @Override
    public boolean isDebugEnable() {
        return delegateLogger.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnable() {
        return delegateLogger.isInfoEnabled();
    }

    @Override
    public boolean isWarnEnable() {
        return delegateLogger.isWarnEnabled();
    }

    @Override
    public boolean isErrorEnable() {
        return delegateLogger.isErrorEnabled();
    }

    @Override
    public void debug(String format) {
        delegateLogger.debug(format);
    }

    @Override
    public void debug(String format, Object... arguments) {
        delegateLogger.debug(format, arguments);
    }

    @Override
    public void error(String format) {
        delegateLogger.error(format);
    }
}
