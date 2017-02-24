package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.api.logging.api.ILog;
import com.a.eye.skywalking.api.logging.api.LogManager;
import com.a.eye.skywalking.api.util.ConfigInitializer;
import com.a.eye.skywalking.api.util.StringUtil;

import java.io.InputStream;
import java.util.Properties;

/**
 * @author pengys5
 */
public class CollectorConfigInitializer {

    private static ILog logger = LogManager.getLogger(CollectorConfigInitializer.class);

    public static void initialize() {
        InputStream configFileStream = CollectorConfigInitializer.class.getResourceAsStream("/collector.config");

        if (configFileStream == null) {
            logger.info("Not provide sky-walking certification documents, sky-walking api run in default config.");
        } else {
            try {
                Properties properties = new Properties();
                properties.load(configFileStream);
                ConfigInitializer.initialize(properties, CollectorConfig.class);
            } catch (Exception e) {
                logger.error("Failed to read the config file, sky-walking api run in default config.", e);
            }
        }

        if (!StringUtil.isEmpty(System.getProperty("collector.hostname"))) {
            CollectorConfig.Collector.hostname = System.getProperty("collector.hostname");
        }
        if (!StringUtil.isEmpty(System.getProperty("collector.port"))) {
            CollectorConfig.Collector.port = System.getProperty("collector.port");
        }
        if (!StringUtil.isEmpty(System.getProperty("collector.cluster"))) {
            CollectorConfig.Collector.cluster = System.getProperty("collector.cluster");
        }
    }
}
