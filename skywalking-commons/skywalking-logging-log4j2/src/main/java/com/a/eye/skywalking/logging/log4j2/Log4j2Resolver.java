package com.a.eye.skywalking.logging.log4j2;

import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogResolver;

/**
 * The <code>LogResolver</code> is an implementation of {@link LogResolver},
 *
 * @author wusheng
 */
public class Log4j2Resolver implements LogResolver {
    @Override
    public ILog getLogger(Class<?> clazz) {
        return new Log4j2Logger(clazz);
    }
}
