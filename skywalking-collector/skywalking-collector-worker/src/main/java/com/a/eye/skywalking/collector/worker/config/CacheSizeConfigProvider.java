package com.a.eye.skywalking.collector.worker.config;

import com.a.eye.skywalking.collector.config.ConfigProvider;

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
