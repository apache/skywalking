package com.a.eye.skywalking.collector.worker.config;

import com.a.eye.skywalking.collector.config.ConfigProvider;

/**
 * @author pengys5
 */
public class WorkerConfigProvider implements ConfigProvider {

    @Override
    public Class configClass() {
        return WorkerConfig.class;
    }

    @Override
    public void cliArgs() {
    }
}
