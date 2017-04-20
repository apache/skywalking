package com.a.eye.skywalking.api.logging;

import com.a.eye.skywalking.api.conf.Constants;
import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.logging.ILog;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.a.eye.skywalking.api.conf.Config.Logging.LEVEL;
import static com.a.eye.skywalking.api.logging.LogLevel.DEBUG;
import static com.a.eye.skywalking.api.logging.LogLevel.ERROR;
import static com.a.eye.skywalking.api.logging.LogLevel.INFO;
import static com.a.eye.skywalking.api.logging.LogLevel.WARN;

/**
 * The <code>EasyLogger</code> is a simple implementation of {@link ILog}.
 *
 * @author wusheng
 */
public class EasyLogger implements ILog {

    private Class targetClass;

    public EasyLogger(Class targetClass) {
        this.targetClass = targetClass;
    }

    private void logger(LogLevel level, String message, Throwable e) {
        WriterFactory.getLogWriter().write(format(level, message, e));
    }

    private String replaceParam(String message, Object... parameters) {
        int startSize = 0;
        int parametersIndex = 0;
        int index;
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

    String format(LogLevel level, String message, Throwable t) {
        return StringUtil.join(' ', level.name(),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),
            targetClass.getSimpleName(),
            ": ",
            message,
            t == null ? "" : format(t)
        );
    }

    String format(Throwable t) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        t.printStackTrace(new java.io.PrintWriter(buf, true));
        String expMessage = buf.toString();
        try {
            buf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Constants.LINE_SEPARATOR + expMessage;
    }

    @Override
    public void info(String format) {
        if (isInfoEnable())
            logger(INFO, format, null);
    }

    @Override
    public void info(String format, Object... arguments) {
        if (isInfoEnable())
            logger(INFO, replaceParam(format, arguments), null);
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (isWarnEnable())
            logger(WARN, replaceParam(format, arguments), null);
    }

    @Override
    public void error(String format, Throwable e) {
        if (isErrorEnable())
            logger(ERROR, format, e);
    }

    @Override
    public void error(Throwable e, String format, Object... arguments) {
        if (isErrorEnable())
            logger(ERROR, replaceParam(format, arguments), e);
    }

    @Override
    public boolean isDebugEnable() {
        return DEBUG.compareTo(LEVEL) >= 0;
    }

    @Override
    public boolean isInfoEnable() {
        return INFO.compareTo(LEVEL) >= 0;
    }

    @Override
    public boolean isWarnEnable() {
        return WARN.compareTo(LEVEL) >= 0;
    }

    @Override
    public boolean isErrorEnable() {
        return ERROR.compareTo(LEVEL) >= 0;
    }

    @Override
    public void debug(String format) {
        if (isDebugEnable()) {
            logger(DEBUG, format, null);
        }
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (isDebugEnable()) {
            logger(DEBUG, replaceParam(format, arguments), null);
        }
    }

    @Override
    public void error(String format) {
        if (isErrorEnable()) {
            logger(ERROR, format, null);
        }
    }
}
