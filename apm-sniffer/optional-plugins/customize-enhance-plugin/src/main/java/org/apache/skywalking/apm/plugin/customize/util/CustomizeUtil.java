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

package org.apache.skywalking.apm.plugin.customize.util;

import java.util.HashMap;
import java.util.Map;

public class CustomizeUtil {

    private static final Map<String, Class> JAVA_CLASS = new HashMap<String, Class>();

    static {
        JAVA_CLASS.put("boolean.class", boolean.class);
        JAVA_CLASS.put("char.class", char.class);
        JAVA_CLASS.put("byte.class", byte.class);
        JAVA_CLASS.put("short.class", short.class);
        JAVA_CLASS.put("int.class", int.class);
        JAVA_CLASS.put("long.class", long.class);
        JAVA_CLASS.put("float.class", float.class);
        JAVA_CLASS.put("double.class", double.class);
        JAVA_CLASS.put("java.util.List", java.util.List.class);
        JAVA_CLASS.put("java.util.Map", java.util.Map.class);
    }

    public static boolean isJavaClass(String className) {
        return JAVA_CLASS.containsKey(className);
    }

    public static Class getJavaClass(String className) {
        return JAVA_CLASS.get(className);
    }

    public static String generateOperationName(String className, String methodName, String[] parameterTypes) {
        StringBuilder operationName = new StringBuilder(className + "." + methodName + "(");
        for (int i = 0; i < parameterTypes.length; i++) {
            operationName.append(CustomizeUtil.isJavaClass(parameterTypes[i]) ? CustomizeUtil.getJavaClass(parameterTypes[i])
                                                                                             .getName() : parameterTypes[i]);
            if (i < (parameterTypes.length - 1)) {
                operationName.append(",");
            }
        }
        operationName.append(")");
        return operationName.toString();
    }

    public static String generateClassDesc(String className, boolean isStatic) {
        return className + ":" + isStatic;
    }

    public static String[] getClassDesc(String enhanceClass) {
        return enhanceClass.split(":");
    }
}
