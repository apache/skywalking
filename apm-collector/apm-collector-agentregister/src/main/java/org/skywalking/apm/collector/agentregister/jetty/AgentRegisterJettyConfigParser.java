package org.skywalking.apm.collector.agentregister.jetty;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class AgentRegisterJettyConfigParser implements ModuleConfigParser {

    private static final String HOST = "host";
    private static final String PORT = "port";
    public static final String CONTEXT_PATH = "contextPath";

    @Override public void parse(Map config) throws ConfigParseException {
        AgentRegisterJettyConfig.CONTEXT_PATH = "/";

        if (ObjectUtils.isEmpty(config) || StringUtils.isEmpty(config.get(HOST))) {
            AgentRegisterJettyConfig.HOST = "localhost";
        } else {
            AgentRegisterJettyConfig.HOST = (String)config.get(HOST);
        }

        if (ObjectUtils.isEmpty(config) || StringUtils.isEmpty(config.get(PORT))) {
            AgentRegisterJettyConfig.PORT = 12800;
        } else {
            AgentRegisterJettyConfig.PORT = (Integer)config.get(PORT);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CONTEXT_PATH))) {
            AgentRegisterJettyConfig.CONTEXT_PATH = (String)config.get(CONTEXT_PATH);
        }
    }
}
