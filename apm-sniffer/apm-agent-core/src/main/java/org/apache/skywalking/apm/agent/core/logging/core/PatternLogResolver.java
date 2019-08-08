package org.apache.skywalking.apm.agent.core.logging.core;

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogResolver;

/**
 * @author alvin
 */
public class PatternLogResolver implements LogResolver {

    @Override
    public ILog getLogger(Class<?> clazz) {
        return new PatternLogger(clazz, Config.Logging.PATTERN);
    }

    @Override
    public ILog getLogger(String clazz) {
        return new PatternLogger(clazz, Config.Logging.PATTERN);
    }
}
