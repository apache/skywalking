package org.skywalking.apm.collector.remote.grpc;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author pengys5
 */
public class RemoteGRPCConfigParser implements ModuleConfigParser {

    private static final String HOST = "host";
    private static final String PORT = "port";

    @Override public void parse(Map config) throws ConfigParseException {
        if (ObjectUtils.isEmpty(config) || StringUtils.isEmpty(config.get(HOST))) {
            RemoteGRPCConfig.HOST = "localhost";
        }
        if (ObjectUtils.isEmpty(config) || StringUtils.isEmpty(config.get(PORT))) {
            RemoteGRPCConfig.PORT = 11800;
        } else {
            RemoteGRPCConfig.PORT = (Integer)config.get(PORT);
        }
    }
}
