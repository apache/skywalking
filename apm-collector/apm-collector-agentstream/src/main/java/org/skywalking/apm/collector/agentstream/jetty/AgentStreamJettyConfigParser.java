package org.skywalking.apm.collector.agentstream.jetty;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class AgentStreamJettyConfigParser implements ModuleConfigParser {

    private static final String HOST = "host";
    private static final String PORT = "port";
    public static final String CONTEXT_PATH = "contextPath";

    @Override public void parse(Map config) throws ConfigParseException {
        AgentStreamJettyConfig.HOST = (String)config.get(HOST);
        AgentStreamJettyConfig.CONTEXT_PATH = "/";

        if (StringUtils.isEmpty(AgentStreamJettyConfig.HOST)) {
            throw new ConfigParseException("");
        }
        if (StringUtils.isEmpty(config.get(PORT))) {
            throw new ConfigParseException("");
        } else {
            AgentStreamJettyConfig.PORT = (Integer)config.get(PORT);
        }
        if (!StringUtils.isEmpty(config.get(CONTEXT_PATH))) {
            AgentStreamJettyConfig.CONTEXT_PATH = (String)config.get(CONTEXT_PATH);
        }
    }
}
