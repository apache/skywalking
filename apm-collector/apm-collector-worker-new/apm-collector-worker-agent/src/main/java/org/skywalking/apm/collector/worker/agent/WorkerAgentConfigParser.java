package org.skywalking.apm.collector.worker.agent;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class WorkerAgentConfigParser implements ModuleConfigParser {

    private final String HOST = "host";
    private final String PORT = "port";

    @Override public void parse(Map config) throws ConfigParseException {
        if (StringUtils.isEmpty(config.get(HOST))) {
            throw new ConfigParseException("");
        }
        WorkerAgentConfig.HOST = (String)config.get(HOST);

        if (StringUtils.isEmpty(config.get(PORT))) {
            throw new ConfigParseException("");
        }
        WorkerAgentConfig.PORT = (Integer)config.get(PORT);
    }
}
