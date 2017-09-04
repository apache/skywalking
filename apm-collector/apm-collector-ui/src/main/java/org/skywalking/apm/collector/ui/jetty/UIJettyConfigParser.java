package org.skywalking.apm.collector.ui.jetty;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class UIJettyConfigParser implements ModuleConfigParser {

    private static final String HOST = "host";
    private static final String PORT = "port";
    public static final String CONTEXT_PATH = "contextPath";

    @Override public void parse(Map config) throws ConfigParseException {
        UIJettyConfig.CONTEXT_PATH = "/";

        if (ObjectUtils.isEmpty(config) || StringUtils.isEmpty(config.get(HOST))) {
            UIJettyConfig.HOST = "localhost";
        } else {
            UIJettyConfig.HOST = (String)config.get(HOST);
        }

        if (ObjectUtils.isEmpty(config) || StringUtils.isEmpty(config.get(PORT))) {
            UIJettyConfig.PORT = 12800;
        } else {
            UIJettyConfig.PORT = (Integer)config.get(PORT);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CONTEXT_PATH))) {
            UIJettyConfig.CONTEXT_PATH = (String)config.get(CONTEXT_PATH);
        }
    }
}
