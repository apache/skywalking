package org.apache.skywalking.apm.agent.core.logging.core;

import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.apache.skywalking.apm.util.StringUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * @author alvin
 */
public class PatternLogger extends EasyLogger {

    public static final String DEFAULT_PATTERN = "${level} ${timestamp} ${threadName} ${className} : ${msg} ${throwable:\"\"}";

    String createPlaceHolderByKey(String key) {
        return "${" + key + "}";
    }

    private String pattern;

    public PatternLogger(Class targetClass, String pattern) {
        this(targetClass.getSimpleName(), pattern);
    }

    public PatternLogger(String targetClass, String pattern) {
        super(targetClass);
        this.setPattern(pattern);
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        if (StringUtil.isEmpty(pattern)) {
            pattern = DEFAULT_PATTERN;
        }
        this.pattern = pattern;
    }

    @Override
    String format(LogLevel level, String message, Throwable t) {
        Properties props = buildContext(level, message, t);
        return PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(getPattern(), props);
    }

    private Properties buildContext(LogLevel level, String message, Throwable t) {
        Properties props = new Properties();
        props.put("level", level.name());
        props.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date()));
        props.put("threadName", Thread.currentThread().getName());
        props.put("className", targetClass);
        props.put("msg", message);
        props.put("throwable", t == null ? "" : format(t));
        return props;
    }
}
