package org.skywalking.apm.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Init a class's static fields by a {@link Properties},
 * including static fields and static inner classes.
 * <p>
 * Created by wusheng on 2017/1/9.
 */
public class ConfigInitializer {
    private static final Logger logger = Logger.getLogger(ConfigInitializer.class.getName());

    public static void initialize(Properties properties, Class<?> rootConfigType) throws IllegalAccessException {
        initNextLevel(properties, rootConfigType, new ConfigDesc());
    }

    private static void initNextLevel(Properties properties, Class<?> recentConfigType,
        ConfigDesc parentDesc) throws IllegalArgumentException, IllegalAccessException {
        for (Field field : recentConfigType.getFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
                String configKey = (parentDesc + "." + field.getName()).toLowerCase();
                String value = properties.getProperty(configKey);
                if (value != null) {
                    Class<?> type = field.getType();
                    if (type.equals(int.class))
                        field.set(null, Integer.valueOf(value));
                    else if (type.equals(String.class))
                        field.set(null, value);
                    else if (type.equals(long.class))
                        field.set(null, Long.valueOf(value));
                    else if (type.equals(boolean.class))
                        field.set(null, Boolean.valueOf(value));
                    else if (type.equals(List.class))
                        field.set(null, convert2List(value));
                    else if (type.isEnum())
                        field.set(null, Enum.valueOf((Class<Enum>)type, value.toUpperCase()));
                }
            }
        }
        for (Class<?> innerConfiguration : recentConfigType.getClasses()) {
            parentDesc.append(innerConfiguration.getSimpleName());
            initNextLevel(properties, innerConfiguration, parentDesc);
            parentDesc.removeLastDesc();
        }
    }

    private static List convert2List(String value) {
        List result = new ArrayList();
        if (StringUtil.isEmpty(value)) {
            return result;
        }

        String[] segments = value.split(",");
        for (String segment : segments) {
            result.add(segment);
        }
        return result;
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
