package org.skywalking.apm.collector.cluster.zookeeper;

import java.util.Map;
import org.skywalking.apm.collector.client.zookeeper.ZookeeperConfig;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class ClusterZKConfigParser implements ModuleConfigParser {

    private final String HOST_PORT = "hostPort";
    private final String SESSION_TIMEOUT = "sessionTimeout";

    @Override public void parse(Map config) throws ConfigParseException {
        if (StringUtils.isEmpty(config.get(HOST_PORT))) {
            throw new ConfigParseException("");
        }
        ZookeeperConfig.hostPort = (String)config.get(HOST_PORT);

        if (StringUtils.isEmpty(config.get(SESSION_TIMEOUT))) {
            ZookeeperConfig.sessionTimeout = 1000;
        } else {
            ZookeeperConfig.sessionTimeout = (Integer)config.get(SESSION_TIMEOUT);
        }
    }
}
