package com.a.eye.skywalking.analysis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.Properties;

public class ConfigInitializer {
    private static Logger logger = LoggerFactory.getLogger(ConfigInitializer.class.getName());

    public static void initialize() {
        InputStream inputStream = ConfigInitializer.class.getResourceAsStream("/analysis.conf");
        if (inputStream == null) {
            logger.error("No provider sky-walking certification documents, sky-walking api auto shutdown.");
        } else {
            try {
                Properties properties = new Properties();
                properties.load(inputStream);
                initNextLevel(properties, Config.class, new ConfigDesc());
            } catch (IllegalAccessException e) {
                logger.error("Parsing certification file failed, sky-walking api auto shutdown.");
            } catch (IOException e) {
                logger.error("Failed to read the certification file, sky-walking api auto shutdown.");
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
