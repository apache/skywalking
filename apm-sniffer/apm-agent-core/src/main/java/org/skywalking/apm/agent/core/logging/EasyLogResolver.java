package org.skywalking.apm.agent.core.logging;

import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogResolver;

/**
 * Created by wusheng on 2016/11/26.
 */
public class EasyLogResolver implements LogResolver {
    @Override
    public ILog getLogger(Class<?> clazz) {
        return new EasyLogger(clazz);
    }
}
