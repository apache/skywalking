package org.skywalking.apm.collector.config;

/**
 * @author pengys5
 */
public interface ConfigProvider {
    Class configClass();

    void cliArgs();
}
