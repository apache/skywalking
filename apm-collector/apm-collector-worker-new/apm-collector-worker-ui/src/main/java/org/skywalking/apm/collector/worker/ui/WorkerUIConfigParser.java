package org.skywalking.apm.collector.worker.ui;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class WorkerUIConfigParser implements ModuleConfigParser {

    private final String HOST = "host";
    private final String PORT = "port";
    private final String CONTEXT_PATH = "context_path";

    @Override public void parse(Map config) throws ConfigParseException {
        if (StringUtils.isEmpty(config.get(HOST))) {
            throw new ConfigParseException("HOST must be require");
        }
        WorkerUIConfig.HOST = (String)config.get(HOST);

        if (StringUtils.isEmpty(config.get(PORT))) {
            throw new ConfigParseException("");
        }
        WorkerUIConfig.PORT = (Integer)config.get(PORT);

        if (StringUtils.isEmpty(config.get(CONTEXT_PATH))) {
            WorkerUIConfig.CONTEXT_PATH = "/";
        } else {
            WorkerUIConfig.CONTEXT_PATH = (String)config.get(CONTEXT_PATH);
        }
    }
}
