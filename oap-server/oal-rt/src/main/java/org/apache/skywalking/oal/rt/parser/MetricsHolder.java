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

package org.apache.skywalking.oal.rt.parser;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;

@SuppressWarnings("UnstableApiUsage")
public class MetricsHolder {
    private static final Map<String, Class<? extends Metrics>> REGISTER = new HashMap<>();
    private static volatile boolean INITIALIZED = false;

    private static void init() throws IOException {
        ClassPath classpath = ClassPath.from(MetricsHolder.class.getClassLoader());
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");
        for (ClassPath.ClassInfo classInfo : classes) {
            Class<?> aClass = classInfo.load();

            if (aClass.isAnnotationPresent(MetricsFunction.class)) {
                MetricsFunction metricsFunction = aClass.getAnnotation(MetricsFunction.class);
                REGISTER.put(
                    metricsFunction.functionName(),
                    (Class<? extends Metrics>) aClass
                );
            }
        }
    }

    @SneakyThrows
    public static Class<? extends Metrics> find(String functionName) {
        if (!INITIALIZED) {
            init();
            INITIALIZED = true;
        }

        Class<? extends Metrics> metricsClass = REGISTER.get(functionName);
        if (metricsClass == null) {
            throw new IllegalArgumentException("Can't find metrics, " + functionName);
        }
        return metricsClass;
    }
}
