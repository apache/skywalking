package org.skywalking.apm.agent.core.conf;

import org.skywalking.apm.util.StringUtil;
import org.skywalking.apm.util.ConfigInitializer;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

/**
 * The <code>SnifferConfigInitializer</code> initializes all configs in several way.
 *
 * @author wusheng
 * @see {@link #initialize()}, to learn more about how to initialzie.
 */
public class SnifferConfigInitializer {
    private static final ILog logger = LogManager.getLogger(SnifferConfigInitializer.class);
    private static String CONFIG_FILE_NAME = "/sky-walking.config";

    /**
     * Try to locate config file, named {@link #CONFIG_FILE_NAME}, in following order:
     * 1. Path from SystemProperty. {@link #loadConfigBySystemProperty()}
     * 2. class path.
     * 3. Path, where agent is. {@link #loadConfigFromAgentFolder()}
     * <p>
     * If no found in any path, agent is still going to run in default config, {@link Config},
     * but in initialization steps, these following configs must be set, by config file or system properties:
     * <p>
     * 1. applicationCode. "-DapplicationCode=" or  {@link Config.Agent#APPLICATION_CODE}
     * 2. servers. "-Dservers=" or  {@link Config.Collector#SERVERS}
     */
    public static void initialize() {
        InputStream configFileStream;

        configFileStream = loadConfigBySystemProperty();

        if (configFileStream == null) {
            configFileStream = SnifferConfigInitializer.class.getResourceAsStream(CONFIG_FILE_NAME);

            if (configFileStream == null) {
                logger.info("No {} file found in class path.", CONFIG_FILE_NAME);
                configFileStream = loadConfigFromAgentFolder();
            } else {
                logger.info("{} file found in class path.", CONFIG_FILE_NAME);
            }
        }

        if (configFileStream == null) {
            logger.info("No {} found, sky-walking is going to run in default config.", CONFIG_FILE_NAME);
        } else {
            try {
                Properties properties = new Properties();
                properties.load(configFileStream);
                ConfigInitializer.initialize(properties, Config.class);
            } catch (Exception e) {
                logger.error("Failed to read the config file, sky-walking is going to run in default config.", e);
            }
        }

        String applicationCode = System.getProperty("applicationCode");
        if (!StringUtil.isEmpty(applicationCode)) {
            Config.Agent.APPLICATION_CODE = applicationCode;
        }
        String servers = System.getProperty("servers");
        if (!StringUtil.isEmpty(servers)) {
            Config.Collector.SERVERS = servers;
        }

        if (StringUtil.isEmpty(Config.Agent.APPLICATION_CODE)) {
            throw new ExceptionInInitializerError("'-DapplicationCode=' is missing.");
        }
        if (StringUtil.isEmpty(Config.Collector.SERVERS)) {
            throw new ExceptionInInitializerError("'-Dservers=' is missing.");
        }
    }

    /**
     * Load the config file by the path, which is provided by system property, usually with a "-DconfigPath=" arg.
     *
     * @return the config file {@link InputStream}, or null if not exist.
     */
    private static InputStream loadConfigBySystemProperty() {
        String configPath = System.getProperty("configPath");
        if (StringUtil.isEmpty(configPath)) {
            return null;
        }
        File configFile = new File(configPath, CONFIG_FILE_NAME);
        if (configFile.exists() && configFile.isFile()) {
            try {
                logger.info("{} found in path {}, according system property.", CONFIG_FILE_NAME, configPath);
                return new FileInputStream(configFile);
            } catch (FileNotFoundException e) {
                logger.error(e, "Fail to load {} in path {}, according system property.", CONFIG_FILE_NAME, configPath);
            }
        }

        logger.info("No {} found in path {}, according system property.", CONFIG_FILE_NAME, configPath);
        return null;
    }

    /**
     * Load the config file, where the agent jar is.
     *
     * @return the config file {@link InputStream}, or null if not exist.
     */
    private static InputStream loadConfigFromAgentFolder() {
        String agentBasePath = initAgentBasePath();
        if (!StringUtil.isEmpty(agentBasePath)) {
            File configFile = new File(agentBasePath, CONFIG_FILE_NAME);
            if (configFile.exists() && configFile.isFile()) {
                try {
                    logger.info("{} file found in agent folder.", CONFIG_FILE_NAME);
                    return new FileInputStream(configFile);
                } catch (FileNotFoundException e) {
                    logger.error(e, "Fail to load {} in path {}, according auto-agent-folder mechanism.", CONFIG_FILE_NAME, agentBasePath);
                }
            }
        }

        logger.info("No {} file found in agent folder.", CONFIG_FILE_NAME);
        return null;
    }

    /**
     * Try to allocate the skywalking-agent.jar
     * Some config files or output resources are from this path.
     *
     * @return he path, where the skywalking-agent.jar is
     */
    private static String initAgentBasePath() {
        String classResourcePath = SnifferConfigInitializer.class.getName().replaceAll("\\.", "/") + ".class";

        URL resource = SnifferConfigInitializer.class.getClassLoader().getSystemClassLoader().getResource(classResourcePath);
        if (resource != null) {
            String urlString = resource.toString();
            logger.debug(urlString);
            urlString = urlString.substring(urlString.indexOf("file:"), urlString.indexOf('!'));
            File agentJarFile = null;
            try {
                agentJarFile = new File(new URL(urlString).getFile());
            } catch (MalformedURLException e) {
                logger.error(e, "Can not locate agent jar file by url:", urlString);
            }
            if (agentJarFile.exists()) {
                return agentJarFile.getParentFile().getAbsolutePath();
            }
        }

        logger.info("Can not locate agent jar file.");
        return null;
    }
}
