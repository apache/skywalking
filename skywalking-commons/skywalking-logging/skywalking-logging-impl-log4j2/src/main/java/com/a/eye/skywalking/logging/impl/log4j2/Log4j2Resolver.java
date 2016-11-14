package com.a.eye.skywalking.logging.impl.log4j2;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogResolver;
import org.apache.logging.log4j.LogManager;

/**
 * Created by wusheng on 2016/11/11.
 */
public class Log4j2Resolver implements LogResolver {
    @Override
    public ILog getLogger(Class<?> clazz) {
        return new Log4j2Logger(LogManager.getLogger(clazz));
    }
}
