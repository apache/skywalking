/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.skywalking.apm.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Init a class's static fields by a {@link Properties}, including static fields and static inner classes.
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
                Class<?> type = field.getType();

                if (type.equals(Map.class)) {
                    /*
                     * Map config format is, config_key[map_key]=map_value
                     * Such as plugin.opgroup.resttemplate.rule[abc]=/url/path
                     */
                    // Deduct two generic types of the map
                    ParameterizedType genericType = (ParameterizedType)field.getGenericType();
                    Type[] argumentTypes = genericType.getActualTypeArguments();

                    Type keyType = null;
                    Type valueType = null;
                    if (argumentTypes != null && argumentTypes.length == 2) {
                        // Get key type and value type of the map
                        keyType = argumentTypes[0];
                        valueType = argumentTypes[1];
                    }
                    Map map = (Map)field.get(null);
                    // Set the map from config key and properties
                    setForMapType(configKey, map, properties, keyType, valueType);
                } else {
                    /*
                     * Others typical field type
                     */
                    String value = properties.getProperty(configKey);
                    // Convert the value into real type
                    Object convertedValue = convertToTypicalType(type, value);
                    if (convertedValue != null) {
                        field.set(null, convertedValue);
                    }
                }
            }
        }
        for (Class<?> innerConfiguration : recentConfigType.getClasses()) {
            parentDesc.append(innerConfiguration.getSimpleName());
            initNextLevel(properties, innerConfiguration, parentDesc);
            parentDesc.removeLastDesc();
        }
    }

    /**
     * Convert string value to typical type.
     * @param type type to convert
     * @param value string value to be converted
     * @return converted value or null
     */
    private static Object convertToTypicalType(Type type, String value) {
        if (value == null || type == null) {
            return null;
        }

        Object result = null;
        if (String.class.equals(type)) {
            result = value;
        } else if (int.class.equals(type) || Integer.class.equals(type)) {
            result = Integer.valueOf(value);
        } else if (long.class.equals(type) || Long.class.equals(type)) {
            result = Long.valueOf(value);
        } else if (boolean.class.equals(type) || Boolean.class.equals(type)) {
            result = Boolean.valueOf(value);
        } else if (float.class.equals(type) || Float.class.equals(type)) {
            result = Boolean.valueOf(value);
        } else if (double.class.equals(type) || Double.class.equals(type)) {
            result = Double.valueOf(value);
        } else if (List.class.equals(type)) {
            result = convert2List(value);
        } else if (type instanceof Class) {
            Class<?> clazz = (Class<?>)type;
            if (clazz.isEnum()) {
                result = Enum.valueOf((Class<Enum>)type, value.toUpperCase());
            }
        }
        return result;
    }

    /**
     * Set map items.
     * @param configKey config key must not be null
     * @param map map to set must not be null
     * @param properties properties must not be null
     * @param keyType  key type of the map
     * @param valueType value type of the map
     */
    private static void setForMapType(String configKey, Map<Object, Object> map, Properties properties,
        final Type keyType,
        final Type valueType) {

        Objects.requireNonNull(configKey);
        Objects.requireNonNull(map);
        Objects.requireNonNull(properties);

        String prefix = configKey + "[";
        String suffix = "]";

        properties.forEach((propertyKey, propertyValue) -> {
            String propertyStringKey = propertyKey.toString();
            if (propertyStringKey.startsWith(prefix) && propertyStringKey.endsWith(suffix)) {
                String itemKey = propertyStringKey.substring(prefix.length(), propertyStringKey.length() - suffix.length());
                Object keyObj;
                Object valueObj;

                keyObj = convertToTypicalType(keyType, itemKey);
                valueObj = convertToTypicalType(valueType, propertyValue.toString());

                if (keyObj == null) {
                    keyObj = itemKey;
                }

                if (valueObj == null) {
                    valueObj = propertyValue;
                }

                map.put(keyObj, valueObj);
            }
        });
    }

    private static List<String> convert2List(String value) {
        if (StringUtil.isEmpty(value)) {
            return Collections.emptyList();
        }
        List<String> result = new LinkedList<>();

        String[] segments = value.split(",");
        for (String segment : segments) {
            String trimmedSegment = segment.trim();
            if (StringUtil.isNotEmpty(trimmedSegment)) {
                result.add(trimmedSegment);
            }
        }
        return result;
    }

}

class ConfigDesc {
    private LinkedList<String> descs = new LinkedList<>();

    void append(String currentDesc) {
        if (StringUtil.isNotEmpty(currentDesc)) {
            descs.addLast(currentDesc);
        }
    }

    void removeLastDesc() {
        descs.removeLast();
    }

    @Override
    public String toString() {
        return String.join(".", descs);
    }
}
