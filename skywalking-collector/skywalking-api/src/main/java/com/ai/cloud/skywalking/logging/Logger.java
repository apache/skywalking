package com.ai.cloud.skywalking.logging;


import com.ai.cloud.skywalking.protocol.util.LoggingUtil;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by xin on 16-6-23.
 */
public class Logger {

    private Class toBeLoggerClass;

    public Logger(Class toBeLoggerClass) {
        this.toBeLoggerClass = toBeLoggerClass;
    }

    public void logger(String level, String message, Throwable e) {
        Throwable dummyException = new Throwable();
        StackTraceElement locations[] = dummyException.getStackTrace();

        if (locations != null && locations.length > 2) {
            SyncFileWriter.instance().write(formatMessage(level, message, locations[2]));
        }

        if (e != null) {
            SyncFileWriter.instance().write(LoggingUtil.fetchThrowableStack(e));
        }
    }


    public void error(String message, Throwable e) {
        logger(ERROR, message, e);
    }

    public void error(String message) {
        error(message, null);
    }

    public void warn(String message, Object... parameters) {
        String tmpMessage = replaceParameter(message, parameters);
        logger(WARN, tmpMessage, null);
    }


    public void debug(String message) {
        logger(DEBUG, message, null);
    }

    public void debug(Object message) {
        debug(message.toString());
    }

    public void info(Object message) {
        info(message.toString());
    }

    public void info(String message) {
        logger(INFO, message, null);
    }

    public void debug(String message, Object... parameters) {
        debug(replaceParameter(message, parameters));
    }

    public void error(String message, Object[] parameters, Throwable throwable) {
        logger(ERROR, replaceParameter(message, parameters), throwable);
    }

    public void info(String message, Object paramter) {
        info(replaceParameter(message, new Object[]{paramter}));
    }


    private String replaceParameter(String message, Object... parameters) {
        int startSize = 0;
        int parametersIndex = 0;
        int index = -1;
        String tmpMessage = message;
        while ((index = message.indexOf("{}", startSize)) != -1) {
            if (parametersIndex >= parameters.length) {
                break;
            }

            tmpMessage = tmpMessage.replaceFirst("\\{\\}", parameters[parametersIndex++].toString());
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


}
