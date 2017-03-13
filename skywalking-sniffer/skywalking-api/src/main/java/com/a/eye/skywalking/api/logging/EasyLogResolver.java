package com.a.eye.skywalking.api.logging;

import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogResolver;

/**
 * Created by wusheng on 2016/11/26.
 */
public class EasyLogResolver implements LogResolver {
    @Override
    public ILog getLogger(Class<?> clazz) {
        return new EasyLogger(clazz);
    }
}
