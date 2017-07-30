package org.skywalking.apm.collector.agentjvm.grpc;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class AgentJVMGRPCConfigParser implements ModuleConfigParser {

    private static final String HOST = "host";
    private static final String PORT = "port";

    @Override public void parse(Map config) throws ConfigParseException {
        if (ObjectUtils.isEmpty(config) || StringUtils.isEmpty(config.get(HOST))) {
            AgentJVMGRPCConfig.HOST = "localhost";
        } else {
            AgentJVMGRPCConfig.HOST = (String)config.get(HOST);
        }

        if (ObjectUtils.isEmpty(config) || StringUtils.isEmpty(config.get(PORT))) {
            AgentJVMGRPCConfig.PORT = 11800;
        } else {
            AgentJVMGRPCConfig.PORT = (Integer)config.get(PORT);
        }
    }
}
