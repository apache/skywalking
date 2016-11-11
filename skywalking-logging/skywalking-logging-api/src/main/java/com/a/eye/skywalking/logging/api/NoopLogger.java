package com.a.eye.skywalking.logging.api;


/**
 * Created by xin on 2016/11/10.
 */
public class NoopLogger implements ILog{
    public static final ILog INSTANCE = new NoopLogger();

    @Override
    public void info(String message) {

    }

    @Override
    public void info(String format, Object... arguments) {

    }

    @Override
    public void error(String format, Throwable e) {

    }

    @Override
    public void error(String format, Object argument, Throwable e) {
    }
}
