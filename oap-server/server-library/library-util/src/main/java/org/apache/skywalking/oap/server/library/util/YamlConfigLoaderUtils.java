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

package org.apache.skywalking.oap.server.library.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public class YamlConfigLoaderUtils {

    public static void replacePropertyAndLog(final String propertyName,
                                             final Object propertyValue,
                                             final Properties target,
                                             final Object providerName,
                                             final Yaml yaml) {
        final String valueString = PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(
            String.valueOf(propertyValue), target);
        if (valueString.trim().length() == 0) {
            target.replace(propertyName, valueString);
            log.info("Provider={} config={} has been set as an empty string", providerName, propertyName);
        } else {
            // Use YAML to do data type conversion.
            final Object replaceValue = convertValueString(valueString, yaml);
            if (replaceValue != null) {
                target.replace(propertyName, replaceValue);
            }
        }
    }

    public static Object convertValueString(final String valueString, final Yaml yaml) {
        try {
            Object replaceValue = yaml.load(valueString);
            if (replaceValue instanceof String || replaceValue instanceof Integer || replaceValue instanceof Long || replaceValue instanceof Boolean || replaceValue instanceof ArrayList) {
                return replaceValue;
            } else {
                return valueString;
            }
        } catch (Exception e) {
            log.warn("yaml convert value type error, use origin values string. valueString={}", valueString, e);
            return valueString;
        }
    }

    public static void copyProperties(final Object dest,
                                      final Properties src,
                                      final String moduleName,
                                      final String providerName) throws IllegalAccessException {
        if (dest == null) {
            return;
        }
        Enumeration<?> propertyNames = src.propertyNames();
        while (propertyNames.hasMoreElements()) {
            String propertyName = (String) propertyNames.nextElement();
            Class<?> destClass = dest.getClass();
            try {
                Field field = getDeclaredField(destClass, propertyName);
                field.setAccessible(true);
                field.set(dest, src.get(propertyName));
            } catch (NoSuchFieldException e) {
                log.warn(
                    propertyName + " setting is not supported in " + providerName + " provider of " + moduleName + " module");
            }
        }
    }

    public static Field getDeclaredField(final Class<?> destClass, final String fieldName) throws NoSuchFieldException {
        if (destClass != null) {
            Field[] fields = destClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().equals(fieldName)) {
                    return field;
                }
            }
            return getDeclaredField(destClass.getSuperclass(), fieldName);
        }

        throw new NoSuchFieldException();
    }
}
