package com.a.eye.skywalking.logging;


import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.protocol.util.LoggingUtil;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by xin on 16-6-23.
 */
public class EasyLogger implements ILog {

    private Class toBeLoggerClass;

    public EasyLogger(Class toBeLoggerClass) {
        this.toBeLoggerClass = toBeLoggerClass;
    }

    public void logger(String level, String message, Throwable e) {
        Throwable dummyException = new Throwable();
        StackTraceElement locations[] = dummyException.getStackTrace();

        if (locations != null && locations.length > 2) {
            if(ERROR.equals(level) || WARN.equals(level)){
                WriterFactory.getLogWriter().writeError(formatMessage(level, message, locations[2]));
            }else {
                WriterFactory.getLogWriter().write(formatMessage(level, message, locations[2]));
            }
        }

        if (e != null) {
            WriterFactory.getLogWriter().writeError(LoggingUtil.fetchThrowableStack(e));
        }
    }


    private String replaceParam(String message, Object... parameters) {
        int startSize = 0;
        int parametersIndex = 0;
        int index = -1;
        String tmpMessage = message;
        while ((index = message.indexOf("{}", startSize)) != -1) {
            if (parametersIndex >= parameters.length) {
                break;
            }

            tmpMessage = tmpMessage.replaceFirst("\\{\\}", URLEncoder.encode(String.valueOf(parameters[parametersIndex++])));
            startSize = index + 2;
        }
        return tmpMessage;
    }


    private String formatMessage(String level, String message, StackTraceElement caller) {
        return level + " " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " "
                + caller.getClassName() + "." + caller.getMethodName() + "(" + caller.getFileName() + ":" + caller.getLineNumber() + ") " + message;
    }


    private static final String ERROR = "ERROR";
    private static final String WARN = "WARN";
    private static final String DEBUG = "DEBUG";
    private static final String INFO = "INFO";


    @Override
    public void info(String format) {
        logger(INFO, format, null);
    }

    @Override
    public void info(String format, Object... arguments) {
        logger(INFO, replaceParam(INFO, format, arguments), null);
    }

    @Override
    public void warn(String format, Object... arguments) {
        logger(WARN, replaceParam(WARN, format, arguments), null);
    }

    @Override
    public void warn(String format, Object arguments, Throwable e) {
        logger(WARN, replaceParam(WARN, format, arguments), e);
    }

    @Override
    public void error(String format, Throwable e) {
        logger(ERROR, format, e);
    }

    @Override
    public void error(String format, Object arguments, Throwable e) {
        logger(ERROR, replaceParam(ERROR, format, arguments), e);
    }

    @Override
    public boolean isDebugEnable() {
        return true;
    }

    @Override
    public boolean isInfoEnable() {
        return true;
    }

    @Override
    public boolean isWarnEnable() {
        return true;
    }

    @Override
    public boolean isErrorEnable() {
        return true;
    }

    @Override
    public void debug(String format) {
        logger(DEBUG, format, null);
    }

    @Override
    public void debug(String format, Object... arguments) {
        logger(DEBUG, replaceParam(DEBUG, format, arguments), null);
    }
}
