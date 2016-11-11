package com.a.eye.skywalking.logging;

/**
 * Created by xin on 2016/11/10.
 */
public interface LogResolver {
    ILog getLogger(Class<?> clazz);
}
