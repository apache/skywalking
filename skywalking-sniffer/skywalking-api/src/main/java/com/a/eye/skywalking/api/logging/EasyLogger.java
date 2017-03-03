package com.a.eye.skywalking.api.logging;


import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.a.eye.skywalking.api.logging.LogLevel.*;

/**
 * Created by xin on 16-6-23.
 */
public class EasyLogger implements ILog {

    private Class toBeLoggerClass;

    public EasyLogger(Class toBeLoggerClass) {
        this.toBeLoggerClass = toBeLoggerClass;
    }

    public void logger(LogLevel level, String message, Throwable e) {
        Throwable dummyException = new Throwable();
        StackTraceElement locations[] = dummyException.getStackTrace();

        if (locations != null && locations.length > 2) {
            if (ERROR.equals(level) || WARN.equals(level)) {
                WriterFactory.getLogWriter().writeError(formatMessage(level, message, locations[2]));
            } else {
                WriterFactory.getLogWriter().write(formatMessage(level, message, locations[2]));
            }
        }

        if (e != null) {
            WriterFactory.getLogWriter().writeError(ThrowableFormatter.format(e));
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


    private String formatMessage(LogLevel level, String message, StackTraceElement caller) {
        return level + " " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " "
                + caller.getClassName() + "." + caller.getMethodName() + "(" + caller.getFileName() + ":" + caller.getLineNumber() + ") " + message;
    }

    @Override
    public void info(String format) {
        logger(INFO, format, null);
    }

    @Override
    public void info(String format, Object... arguments) {
        logger(INFO, replaceParam(format, arguments), null);
    }

    @Override
    public void warn(String format, Object... arguments) {
        logger(WARN, replaceParam(format, arguments), null);
    }

    @Override
    public void error(String format, Throwable e) {
        logger(ERROR, format, e);
    }

    @Override
    public void error(Throwable e, String format, Object... arguments) {
        logger(ERROR, replaceParam(format, arguments), e);
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
        logger(DEBUG, replaceParam(format, arguments), null);
    }

    @Override
    public void error(String format) {
        logger(ERROR, format, null);
    }
}
