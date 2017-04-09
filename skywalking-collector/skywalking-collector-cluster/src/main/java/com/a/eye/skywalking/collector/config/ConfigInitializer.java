package com.a.eye.skywalking.collector.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * @author pengys5
 */
public enum ConfigInitializer {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(ConfigInitializer.class);

    public void initialize() throws IOException, IllegalAccessException {
        InputStream configFileStream = ConfigInitializer.class.getResourceAsStream("/collector.config");
        initializeConfigFile(configFileStream);

        ServiceLoader<ConfigProvider> configProviders = ServiceLoader.load(ConfigProvider.class);
        for (ConfigProvider provider : configProviders) {
            provider.cliArgs();
        }
    }

    private void initializeConfigFile(InputStream configFileStream) throws IOException, IllegalAccessException {
        ServiceLoader<ConfigProvider> configProviders = ServiceLoader.load(ConfigProvider.class);
        Properties properties = new Properties();
        properties.load(configFileStream);

        for (ConfigProvider provider : configProviders) {
            logger.info("configProvider provider name: %s", provider.getClass().getName());
            Class configClass = provider.configClass();
            com.a.eye.skywalking.api.util.ConfigInitializer.initialize(properties, configClass);
        }
    }
}
