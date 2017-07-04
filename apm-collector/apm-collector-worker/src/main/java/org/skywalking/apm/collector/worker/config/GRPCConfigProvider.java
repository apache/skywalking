package org.skywalking.apm.collector.worker.config;

import org.skywalking.apm.collector.config.ConfigProvider;
import org.skywalking.apm.util.StringUtil;

/**
 * @author pengys5
 */
public class GRPCConfigProvider implements ConfigProvider {

    @Override public Class configClass() {
        return GRPCConfig.class;
    }

    @Override public void cliArgs() {
        if (!StringUtil.isEmpty(System.getProperty("grpc.PORT"))) {
            GRPCConfig.GRPC.PORT = System.getProperty("grpc.PORT");
        }
    }
}
