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

package org.apache.skywalking.apm.agent.core.util;

import java.lang.reflect.Method;

/**
 * According to the input parameter, return the OperationName for the span record, It can determine the unique method
 */

public class MethodUtil {

    public static String generateOperationName(Method method) {
        StringBuilder operationName = new StringBuilder(method.getDeclaringClass()
                                                              .getName() + "." + method.getName() + "(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            operationName.append(parameterTypes[i].getName());
            if (i < (parameterTypes.length - 1)) {
                operationName.append(",");
            }
        }
        operationName.append(")");
        return operationName.toString();
    }

    /**
     * This is a low-performance method, recommand to use this when have to, make sure it is only executed once and the
     * result is being cached.
     */
    public static boolean isMethodExist(ClassLoader classLoader, String className, String methodName,
                                        String... parameterTypes) {
        try {
            Class<?> clazz = Class.forName(className, true, classLoader);
            if (parameterTypes == null || parameterTypes.length == 0) {
                clazz.getDeclaredMethod(methodName);
                return true;
            } else {
                Method[] declaredMethods = clazz.getDeclaredMethods();
                for (Method declaredMethod : declaredMethods) {
                    if (declaredMethod.getName().equals(methodName)
                        && isParameterTypesEquals(declaredMethod.getParameterTypes(), parameterTypes)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            //ignore
        }
        return false;
    }

    private static boolean isParameterTypesEquals(Class<?>[] parameterTypeClazz, String[] parameterTypeString) {
        if (parameterTypeClazz == null) {
            return false;
        }
        if (parameterTypeClazz.length != parameterTypeString.length) {
            return false;
        }
        for (int i = 0; i < parameterTypeClazz.length; i++) {
            if (!parameterTypeClazz[i].getName().equals(parameterTypeString[i])) {
                return false;
            }
        }
        return true;

    }
}
