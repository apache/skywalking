package com.a.eye.skywalking.api.conf;

import com.a.eye.skywalking.api.logging.ILog;
import com.a.eye.skywalking.api.logging.LogManager;
import com.a.eye.skywalking.api.util.ConfigInitializer;
import com.a.eye.skywalking.api.util.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class SnifferConfigInitializer {
	private static ILog logger = LogManager.getLogger(SnifferConfigInitializer.class);

    public static void initialize() {
        InputStream configFileStream;
        if (Config.SkyWalking.IS_PREMAIN_MODE) {
            configFileStream = fetchAuthFileInputStream();
        } else {
            configFileStream = SnifferConfigInitializer.class.getResourceAsStream("/sky-walking.config");
        }

        if (configFileStream == null) {
            logger.info("Not provide sky-walking certification documents, sky-walking api run in default config.");
        } else {
            try {
                Properties properties = new Properties();
                properties.load(configFileStream);
                ConfigInitializer.initialize(properties, Config.class);
            } catch (Exception e) {
                logger.error("Failed to read the config file, sky-walking api run in default config.", e);
            }
        }
        Config.SkyWalking.USERNAME = System.getProperty("username");
        Config.SkyWalking.APPLICATION_CODE = System.getProperty("applicationCode");
        Config.SkyWalking.SERVERS = System.getProperty("servers");

        if(StringUtil.isEmpty(Config.SkyWalking.USERNAME)){
            throw new ExceptionInInitializerError("'-Dusername=' is missing.");
        }
        if(StringUtil.isEmpty(Config.SkyWalking.APPLICATION_CODE)){
            throw new ExceptionInInitializerError("'-DapplicationCode=' is missing.");
        }
        if(StringUtil.isEmpty(Config.SkyWalking.SERVERS)){
            throw new ExceptionInInitializerError("'-Dservers=' is missing.");
        }
    }

    private static InputStream fetchAuthFileInputStream() {
        try {
            return new FileInputStream(Config.SkyWalking.AGENT_BASE_PATH + File.separator + "/sky-walking.config");
        } catch (Exception e) {
            logger.warn("sky-walking.config is missing, use default config.");
            return null;
        }
    }
}
