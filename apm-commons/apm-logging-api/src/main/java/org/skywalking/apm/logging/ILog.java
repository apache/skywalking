package org.skywalking.apm.logging;

/**
 * The Log interface.
 * It's very easy to understand, like any other log-component.
 * Do just like log4j or log4j2 does.
 * <p>
 * Created by xin on 2016/11/10.
 */
public interface ILog {
    void info(String format);

    void info(String format, Object... arguments);

    void warn(String format, Object... arguments);

    void error(String format, Throwable e);

    void error(Throwable e, String format, Object... arguments);

    boolean isDebugEnable();

    boolean isInfoEnable();

    boolean isWarnEnable();

    boolean isErrorEnable();

    void debug(String format);

    void debug(String format, Object... arguments);

    void error(String format);
}
