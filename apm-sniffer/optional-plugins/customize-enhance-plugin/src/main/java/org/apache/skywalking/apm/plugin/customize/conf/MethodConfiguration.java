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

package org.apache.skywalking.apm.plugin.customize.conf;

import org.apache.skywalking.apm.plugin.customize.constants.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default custom enhancement configuration.
 */

public class MethodConfiguration {

    static String getMethod(Map<String, Object> configuration) {
        return (String) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_METHOD);
    }

    static String getClz(Map<String, Object> configuration) {
        return (String) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_CLZ);
    }

    static Boolean isStatic(Map<String, Object> configuration) {
        return (Boolean) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_IS_STATIC);
    }

    static String getMethodName(Map<String, Object> configuration) {
        return (String) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_METHOD_NAME);
    }

    static String[] getArguments(Map<String, Object> configuration) {
        return (String[]) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_ARGUMENTS);
    }

    static void setOperationName(Map<String, Object> configuration, String operationName) {
        configuration.put(Constants.CONFIGURATION_ATTRIBUTE_OPERATION_NAME, operationName);
    }

    static void setStatic(Map<String, Object> configuration, Boolean isStatic) {
        configuration.put(Constants.CONFIGURATION_ATTRIBUTE_IS_STATIC, isStatic);
    }

    @SuppressWarnings("unchecked")
    static void addOperationNameSuffixes(Map<String, Object> configuration, String suffix) {
        List<String> suffixes = (List<String>) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_OPERATION_NAME_SUFFIXES);
        if (suffixes == null) {
            suffixes = new ArrayList<String>();
            suffixes.add(suffix);
            configuration.put(Constants.CONFIGURATION_ATTRIBUTE_OPERATION_NAME_SUFFIXES, suffixes);
        } else {
            suffixes.add(suffix);
        }
    }

    @SuppressWarnings("unchecked")
    static void addTag(Map<String, Object> configuration, String key, String value) {
        Map<String, String> tags = (Map<String, String>) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_TAGS);
        if (tags == null) {
            tags = new HashMap<String, String>();
            tags.put(key, value);
            configuration.put(Constants.CONFIGURATION_ATTRIBUTE_TAGS, tags);
        } else {
            tags.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    static void addLog(Map<String, Object> configuration, String key, String value) {
        Map<String, String> logs = (Map<String, String>) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_LOGS);
        if (logs == null) {
            logs = new HashMap<String, String>();
            logs.put(key, value);
            configuration.put(Constants.CONFIGURATION_ATTRIBUTE_LOGS, logs);
        } else {
            logs.put(key, value);
        }
    }

    static void setClz(Map<String, Object> configuration, String className) {
        configuration.put(Constants.CONFIGURATION_ATTRIBUTE_CLZ, className);
    }

    static void setMethod(Map<String, Object> configuration, String method) {
        configuration.put(Constants.CONFIGURATION_ATTRIBUTE_METHOD, method);
    }

    static void setMethodName(Map<String, Object> configuration, String methodName) {
        configuration.put(Constants.CONFIGURATION_ATTRIBUTE_METHOD_NAME, methodName);
    }

    static void setArguments(Map<String, Object> configuration, String[] arguments) {
        configuration.put(Constants.CONFIGURATION_ATTRIBUTE_ARGUMENTS, arguments);
    }

    public static String getOperationName(Map<String, Object> configuration) {
        return (String) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_OPERATION_NAME);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getTags(Map<String, Object> configuration) {
        return (Map<String, String>) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_TAGS);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getLogs(Map<String, Object> configuration) {
        return (Map<String, String>) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_LOGS);
    }

    @SuppressWarnings("unchecked")
    public static List<String> getOperationNameSuffixes(Map<String, Object> configuration) {
        return (List<String>) configuration.get(Constants.CONFIGURATION_ATTRIBUTE_OPERATION_NAME_SUFFIXES);
    }
}
