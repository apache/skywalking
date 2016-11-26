package com.a.eye.skywalking.conf;

import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.logging.EasyLogger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.Properties;

public class ConfigInitializer {
	private static EasyLogger easyLogger = LogManager.getLogger(ConfigInitializer.class);

    static void initialize(InputStream inputStream) {
        if (inputStream == null) {
            easyLogger.info("Not provide sky-walking certification documents, sky-walking api auto shutdown.");
        } else {
            try {
                Properties properties = new Properties();
                properties.load(inputStream);
                initNextLevel(properties, Config.class, new ConfigDesc());
                AuthDesc.isAuth = Boolean.valueOf(System.getenv(Config.SkyWalking.AUTH_SYSTEM_ENV_NAME));
                easyLogger.info("sky-walking system-env auth : " + AuthDesc.isAuth);
                if(!AuthDesc.isAuth && Config.SkyWalking.AUTH_OVERRIDE){
                	AuthDesc.isAuth = Config.SkyWalking.AUTH_OVERRIDE;
                	easyLogger.info("sky-walking auth override: " + AuthDesc.isAuth);
                }
            } catch (IllegalAccessException e) {
                easyLogger.error("Parsing certification file failed, sky-walking api auto shutdown.", e);
            } catch (IOException e) {
                easyLogger.error("Failed to read the certification file, sky-walking api auto shutdown.", e);
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
