package org.skywalking.apm.collector.cluster.redis;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class ClusterRedisConfigParser implements ModuleConfigParser {

    private final String HOST = "host";
    private final String PORT = "port";

    @Override public void parse(Map config) throws ConfigParseException {
        ClusterRedisConfig.HOST = (String)config.get(HOST);
        ClusterRedisConfig.PORT = ((Integer)config.get(PORT));
        if (StringUtils.isEmpty(ClusterRedisConfig.HOST) || ClusterRedisConfig.PORT == 0) {
            throw new ConfigParseException("");
        }
    }
}
