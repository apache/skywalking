package com.a.eye.skywalking.collector.cluster;

import com.a.eye.skywalking.api.util.ConfigInitializer;
import com.a.eye.skywalking.api.util.StringUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.Properties;

/**
 * <code>ClusterConfigInitializer</code> Contains static methods for setting
 * {@link ClusterConfig} attributes value.
 *
 * <p>
 * The priority of value setting is
 * system property -> collector.config -> {@link ClusterConfig} default value
 * <p>
 *
 * @author pengys5
 */
public class ClusterConfigInitializer {

    private static Logger logger = LogManager.getFormatterLogger(ClusterConfigInitializer.class);

    public static final String ConfigFileName = "collector.config";

    /**
     * Read config file to setting {@link ClusterConfig} then get system property to overwrite it.
     *
     * @param configFileName is the config file name, the file format is key-value pairs
     */
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
        if (!StringUtil.isEmpty(System.getProperty("cluster.seed_nodes"))) {
            ClusterConfig.Cluster.seed_nodes = System.getProperty("cluster.seed_nodes");
        }
    }
}
