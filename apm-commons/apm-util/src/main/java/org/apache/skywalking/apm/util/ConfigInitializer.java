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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

    @SuppressWarnings("unchecked")
    private static void initNextLevel(Properties properties, Class<?> recentConfigType,
        ConfigDesc parentDesc) throws IllegalArgumentException, IllegalAccessException {
        for (Field field : recentConfigType.getFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String configKey = (parentDesc + "." + field.getName()).toLowerCase();
            /*
              Map config format is, config_key[map_key]=map_value
              Such as plugin.opgroup.resttemplate.rule[abc]=/url/path
             */
            if (field.getType().equals(Map.class)) {
                Map map = (Map)field.get(null);
                String prefix = configKey + "[";
                String suffix = "]";
                properties.forEach((key, value) -> {
                    String stringKey = key.toString();
                    if (!stringKey.startsWith(prefix) || !stringKey.endsWith(suffix)) {
                        return;
                    }
                    String itemKey = stringKey.substring(prefix.length(), stringKey.length() - suffix.length());
                    // TODO Maybe we ought to tackle the value type
                    map.put(itemKey, value);
                });
            } else {
                /*
                  Others typical field type
                 */
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

    private static List<String> convert2List(String value) {
        if (StringUtil.isBlank(value)) {
            return Collections.emptyList();
        }

        List<String> result = new LinkedList<>();

        String[] segments = value.split(",");
        for (String segment : segments) {
            String trimmedSegment = segment.trim();
            if (StringUtil.isNotBlank(trimmedSegment)) {
                result.add(trimmedSegment);
            }
        }
        return result;
    }
}

class ConfigDesc {
    private LinkedList<String> descs = new LinkedList<>();

    void append(String currentDesc) {
        descs.push(currentDesc);
    }

    void removeLastDesc() {
        descs.pop();
    }

    @Override
    public String toString() {
        return String.join(".", descs);
    }
}
