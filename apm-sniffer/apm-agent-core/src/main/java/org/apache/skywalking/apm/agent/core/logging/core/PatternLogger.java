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

    public static final String DEFAULT_PATTERN = "%{level} %{timestamp} %{thread} %{class} : %{msg} %{throwable:\"\"}";

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
        return PropertyPlaceholderHelper.LOGGER.replacePlaceholders(getPattern(), props);
    }

    private Properties buildContext(LogLevel level, String message, Throwable t) {
        Properties props = new Properties();
        props.putAll(System.getenv());
        props.put("level", level.name());
        props.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date()));
        props.put("thread", Thread.currentThread().getName());
        props.put("class", targetClass);
        props.put("msg", message);
        props.put("throwable", t == null ? "" : format(t));
        return props;
    }
}
