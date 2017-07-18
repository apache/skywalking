package org.skywalking.apm.collector.agentstream.grpc;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class AgentStreamGRPCConfigParser implements ModuleConfigParser {

    private static final String HOST = "host";
    private static final String PORT = "port";

    @Override public void parse(Map config) throws ConfigParseException {
        AgentStreamGRPCConfig.HOST = (String)config.get(HOST);

        if (StringUtils.isEmpty(AgentStreamGRPCConfig.HOST)) {
            throw new ConfigParseException("");
        }
        if (StringUtils.isEmpty(config.get(PORT))) {
            throw new ConfigParseException("");
        } else {
            AgentStreamGRPCConfig.PORT = (Integer)config.get(PORT);

        }
    }
}
