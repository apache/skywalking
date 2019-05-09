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

package org.apache.skywalking.oal.tool.parser;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;

public class Metrics {
    private static Map<String, Class<? extends org.apache.skywalking.oap.server.core.analysis.metrics.Metrics>> REGISTER = new HashMap<>();

    public static void init() throws IOException {
        ClassPath classpath = ClassPath.from(Metrics.class.getClassLoader());
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");
        for (ClassPath.ClassInfo classInfo : classes) {
            Class<?> aClass = classInfo.load();

            if (aClass.isAnnotationPresent(MetricsFunction.class)) {
                MetricsFunction metricsFunction = aClass.getAnnotation(MetricsFunction.class);
                REGISTER.put(metricsFunction.functionName(), (Class<? extends org.apache.skywalking.oap.server.core.analysis.metrics.Metrics>)aClass);
            }
        }
    }

    public static Class<? extends org.apache.skywalking.oap.server.core.analysis.metrics.Metrics> find(String functionName) {
        String func = functionName;
        Class<? extends org.apache.skywalking.oap.server.core.analysis.metrics.Metrics> metricsClass = REGISTER.get(func);
        if (metricsClass == null) {
            throw new IllegalArgumentException("Can't find metrics.");
        }
        return metricsClass;
    }
}
