package org.skywalking.apm.logging.log4j2;

import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogResolver;

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
