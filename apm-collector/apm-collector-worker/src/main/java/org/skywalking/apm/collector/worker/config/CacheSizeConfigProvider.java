package org.skywalking.apm.collector.worker.config;

import org.skywalking.apm.collector.config.ConfigProvider;

/**
 * @author pengys5
 */
public class CacheSizeConfigProvider implements ConfigProvider {

    @Override
    public Class configClass() {
        return CacheSizeConfig.class;
    }

    @Override
    public void cliArgs() {
    }
}
