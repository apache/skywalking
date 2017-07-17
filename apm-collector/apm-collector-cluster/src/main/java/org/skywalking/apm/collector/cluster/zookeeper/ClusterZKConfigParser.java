package org.skywalking.apm.collector.cluster.zookeeper;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class ClusterZKConfigParser implements ModuleConfigParser {

    private static final String HOST_PORT = "hostPort";
    private static final String SESSION_TIMEOUT = "sessionTimeout";

    @Override public void parse(Map config) throws ConfigParseException {
        ClusterZKConfig.HOST_PORT = (String)config.get(HOST_PORT);
        ClusterZKConfig.SESSION_TIMEOUT = 1000;

        if (StringUtils.isEmpty(ClusterZKConfig.HOST_PORT)) {
            throw new ConfigParseException("");
        }

        if (!StringUtils.isEmpty(config.get(SESSION_TIMEOUT))) {
            ClusterZKConfig.SESSION_TIMEOUT = (Integer)config.get(SESSION_TIMEOUT);
        }
    }
}
