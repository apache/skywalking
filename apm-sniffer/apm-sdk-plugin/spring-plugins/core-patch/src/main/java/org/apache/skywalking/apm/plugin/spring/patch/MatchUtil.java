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
 */

package org.apache.skywalking.apm.plugin.spring.patch;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MatchUtil {

    private static List<Method> METHODS = new ArrayList<Method>(2);

    static {
        METHODS.addAll(Arrays.asList(EnhancedInstance.class.getDeclaredMethods()));
    }

    static boolean isEnhancedMethod(Method targetMethod) {
        for (Method method : METHODS) {
            if (method.getName().equals(targetMethod.getName()) && method.getReturnType()
                                                                         .equals(targetMethod.getReturnType()) && equalParamTypes(method
                .getParameterTypes(), targetMethod.getParameterTypes())) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalParamTypes(Class<?>[] params1, Class<?>[] params2) {
        if (params1.length != params2.length) {
            return false;
        }
        for (int i = 0; i < params1.length; i++) {
            if (!params1[i].equals(params2[i])) {
                return false;
            }
        }
        return true;
    }
}
