package com.a.eye.skywalking.logging;

import com.a.eye.skywalking.logging.api.LogResolver;

/**
 * Created by wusheng on 2016/11/26.
 */
public class EasyLogResolver implements LogResolver {
    @Override
    public com.a.eye.skywalking.logging.api.ILog getLogger(Class<?> clazz) {
        return new EasyLogger(clazz);
    }
}
