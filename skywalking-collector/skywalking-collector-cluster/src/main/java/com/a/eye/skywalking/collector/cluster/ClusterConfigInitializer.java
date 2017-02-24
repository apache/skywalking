package com.a.eye.skywalking.collector.cluster;

import com.a.eye.skywalking.api.logging.api.ILog;
import com.a.eye.skywalking.api.logging.api.LogManager;
import com.a.eye.skywalking.api.util.ConfigInitializer;
import com.a.eye.skywalking.api.util.StringUtil;

import java.io.InputStream;
import java.util.Properties;

/**
 * @author pengys5
 */
public class ClusterConfigInitializer {

    private static ILog logger = LogManager.getLogger(ClusterConfigInitializer.class);

    public static final String ConfigFileName = "collector.config";

    public static void initialize(String configFileName) {
        InputStream configFileStream = ClusterConfigInitializer.class.getResourceAsStream("/" + configFileName);

        if (configFileStream == null) {
            logger.info("Not provide sky-walking certification documents, sky-walking api run in default config.");
        } else {
            try {
                Properties properties = new Properties();
                properties.load(configFileStream);
                ConfigInitializer.initialize(properties, ClusterConfig.class);
            } catch (Exception e) {
                logger.error("Failed to read the config file, sky-walking api run in default config.", e);
            }
        }

        if (!StringUtil.isEmpty(System.getProperty("cluster.current.hostname"))) {
            ClusterConfig.Cluster.Current.hostname = System.getProperty("cluster.current.hostname");
        }
        if (!StringUtil.isEmpty(System.getProperty("cluster.current.port"))) {
            ClusterConfig.Cluster.Current.port = System.getProperty("cluster.current.port");
        }
        if (!StringUtil.isEmpty(System.getProperty("cluster.current.roles"))) {
            ClusterConfig.Cluster.Current.roles = System.getProperty("cluster.current.roles");
        }
        if (!StringUtil.isEmpty(System.getProperty("cluster.nodes"))) {
            ClusterConfig.Cluster.nodes = System.getProperty("cluster.nodes");
        }
    }
}
