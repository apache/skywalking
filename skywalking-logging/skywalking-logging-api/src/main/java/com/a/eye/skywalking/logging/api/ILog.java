package com.a.eye.skywalking.logging.api;

/**
 * Created by xin on 2016/11/10.
 */
public interface ILog {
    void info(String format);

    void info(String format, Object... arguments);

    void error(String format, Throwable e);

    void error(String format, Object argument, Throwable e);
}
