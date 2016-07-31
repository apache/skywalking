package com.ai.cloud.skywalking.conf;

import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;

import static com.ai.cloud.skywalking.conf.Config.SkyWalking.AUTH_OVERRIDE;
import static com.ai.cloud.skywalking.conf.Config.SkyWalking.AUTH_SYSTEM_ENV_NAME;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.Properties;

public class ConfigInitializer {
	private static Logger logger = LogManager.getLogger(ConfigInitializer.class);

    static void initialize(InputStream inputStream) {
        if (inputStream == null) {
            logger.info("No provider sky-walking certification documents, sky-walking api auto shutdown.");
        } else {
            try {
                Properties properties = new Properties();
                properties.load(inputStream);
                initNextLevel(properties, Config.class, new ConfigDesc());
                AuthDesc.isAuth = Boolean.valueOf(System.getenv(AUTH_SYSTEM_ENV_NAME));
                logger.info("sky-walking system-env auth : " + AuthDesc.isAuth);
                if(!AuthDesc.isAuth && AUTH_OVERRIDE){
                	AuthDesc.isAuth = AUTH_OVERRIDE;
                	logger.info("sky-walking auth override: " + AuthDesc.isAuth);
                }
            } catch (IllegalAccessException e) {
                logger.error("Parsing certification file failed, sky-walking api auto shutdown.", e);
            } catch (IOException e) {
                logger.error("Failed to read the certification file, sky-walking api auto shutdown.", e);
            }
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
