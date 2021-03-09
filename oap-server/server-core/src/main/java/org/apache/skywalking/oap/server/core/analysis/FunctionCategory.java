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

package org.apache.skywalking.oap.server.core.analysis;

import java.lang.annotation.Annotation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;

@AllArgsConstructor
@Getter
public enum FunctionCategory {
    METER("meter", MeterFunction.class),
    METRICS("metrics", MetricsFunction.class);
    private final String name;
    private final Class<? extends Annotation> annotationClass;

    /**
     * The unique function name pattern is {function category}-{function name}.
     */
    public static String uniqueFunctionName(final Class<?> aClass) {
        Annotation annotation = doGetAnnotation(aClass, MeterFunction.class);
        if (annotation != null) {
            return (METER.getName() + Const.LINE + ((MeterFunction) annotation).functionName()).toLowerCase();
        }
        annotation = doGetAnnotation(aClass, MetricsFunction.class);
        if (annotation != null) {
            return (METRICS.getName() + Const.LINE + ((MetricsFunction) annotation).functionName()).toLowerCase();
        }
        return "";
    }

    private static Annotation doGetAnnotation(Class<?> clazz, Class<? extends Annotation> annotationClass) {
        if (clazz.equals(Object.class)) {
            return null;
        }
        Annotation[] annotations = clazz.getAnnotations();
        for (final Annotation annotation : annotations) {
            if (annotation.annotationType().equals(annotationClass)) {
                return annotation;
            }
        }
        return doGetAnnotation(clazz.getSuperclass(), annotationClass);
    }
}
