package com.a.eye.skywalking.conf;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.protocol.util.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.Properties;

public class ConfigInitializer {
	private static ILog logger = LogManager.getLogger(ConfigInitializer.class);

    public static void initialize() {
        InputStream configFileStream;
        if (Config.SkyWalking.IS_PREMAIN_MODE) {
            configFileStream = fetchAuthFileInputStream();
        } else {
            configFileStream = ConfigInitializer.class.getResourceAsStream("/sky-walking.auth");
        }

        Config.SkyWalking.USER_ID = System.getProperty("userId");
        Config.SkyWalking.APPLICATION_CODE = System.getProperty("applicationCode");
        Config.SkyWalking.SERVERS = System.getProperty("server");

        if (configFileStream == null) {
            logger.info("Not provide sky-walking certification documents, sky-walking api run in default config.");
        } else {
            try {
                Properties properties = new Properties();
                properties.load(configFileStream);
                initNextLevel(properties, Config.class, new ConfigDesc());
            } catch (Exception e) {
                logger.error("Failed to read the config file, sky-walking api run in default config.", e);
            }
        }

        if(StringUtil.isEmpty(Config.SkyWalking.USER_ID)){
            throw new ExceptionInInitializerError("'-DuserId=' is missing.");
        }
        if(StringUtil.isEmpty(Config.SkyWalking.APPLICATION_CODE)){
            throw new ExceptionInInitializerError("'-DapplicationCode=' is missing.");
        }
        if(StringUtil.isEmpty(Config.SkyWalking.SERVERS)){
            throw new ExceptionInInitializerError("'-Dserver=' is missing.");
        }
    }

    private static void initNextLevel(Properties properties, Class<?> recentConfigType, ConfigDesc parentDesc) throws NumberFormatException, IllegalArgumentException, IllegalAccessException {
        for (Field field : recentConfigType.getFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
                String configKey = (parentDesc + "." +
                        field.getName()).toLowerCase();
                String value = properties.getProperty(configKey);
                if (value != null) {
                    if (field.getType().equals(int.class))
                        field.set(null, Integer.valueOf(value));
                    if (field.getType().equals(String.class))
                        field.set(null, value);
                    if (field.getType().equals(long.class))
                        field.set(null, Long.valueOf(value));
                    if (field.getType().equals(boolean.class))
                        field.set(null, Boolean.valueOf(value));
                }
            }
        }
        for (Class<?> innerConfiguration : recentConfigType.getClasses()) {
            parentDesc.append(innerConfiguration.getSimpleName());
            initNextLevel(properties, innerConfiguration, parentDesc);
            parentDesc.removeLastDesc();
        }
    }

    private static InputStream fetchAuthFileInputStream() {
        try {
            return new FileInputStream(Config.SkyWalking.AGENT_BASE_PATH + File.separator + "/sky-walking.auth");
        } catch (Exception e) {
            logger.error("Error to fetch auth file input stream.", e);
            return null;
        }
    }
}

class ConfigDesc {
    private LinkedList<String> descs = new LinkedList<String>();

    void append(String currentDesc) {
        descs.addLast(currentDesc);
    }

    void removeLastDesc() {
        descs.removeLast();
    }

    @Override
    public String toString() {
        if (descs.size() == 0) {
            return "";
        }
        StringBuilder ret = new StringBuilder(descs.getFirst());
        boolean first = true;
        for (String desc : descs) {
            if (first) {
                first = false;
                continue;
            }
            ret.append(".").append(desc);
        }
        return ret.toString();
    }
}
