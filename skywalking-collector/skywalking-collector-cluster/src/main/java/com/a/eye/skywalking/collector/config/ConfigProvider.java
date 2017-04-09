package com.a.eye.skywalking.collector.config;

/**
 * @author pengys5
 */
public interface ConfigProvider {
    Class configClass();

    void cliArgs();
}
