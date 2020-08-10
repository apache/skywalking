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

import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.BooleanValueFilterMatcher;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.FilterMatcher;

@SuppressWarnings("UnstableApiUsage")
public enum FilterMatchers {
    INSTANCE;

    FilterMatchers() {
        try {
            init();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final Map<String, MatcherInfo> matchersKeyedByType = new HashMap<>();

    private void init() throws IOException {
        final ClassPath classpath = ClassPath.from(FilterMatchers.class.getClassLoader());
        final Set<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");
        for (ClassPath.ClassInfo classInfo : classes) {
            final Class<?> clazz = classInfo.load();

            final FilterMatcher plainFilterMatcher = clazz.getAnnotation(FilterMatcher.class);
            final BooleanValueFilterMatcher booleanFilterMatcher = clazz.getAnnotation(BooleanValueFilterMatcher.class);
            if (plainFilterMatcher != null && booleanFilterMatcher != null) {
                throw new IllegalStateException(
                    "A matcher class can not be annotated with both @FilterMatcher and @BooleanValueFilterMatcher"
                );
            }

            if (plainFilterMatcher != null) {
                for (final String type : plainFilterMatcher.value()) {
                    matchersKeyedByType.put(type, new MatcherInfo(clazz, false));
                }
                if (plainFilterMatcher.value().length == 0) {
                    final String defaultTypeName = StringUtils.uncapitalize(clazz.getSimpleName());
                    matchersKeyedByType.put(defaultTypeName, new MatcherInfo(clazz, false));
                }
            }

            if (booleanFilterMatcher != null) {
                for (final String type : booleanFilterMatcher.value()) {
                    matchersKeyedByType.put(type, new MatcherInfo(clazz, true));
                }
                if (booleanFilterMatcher.value().length == 0) {
                    final String defaultTypeName = StringUtils.uncapitalize(clazz.getSimpleName());
                    matchersKeyedByType.put(defaultTypeName, new MatcherInfo(clazz, true));
                }
            }
        }
    }

    public MatcherInfo find(final String type) {
        if (!matchersKeyedByType.containsKey(type)) {
            throw new IllegalArgumentException("filter expression [" + type + "] not found");
        }
        return matchersKeyedByType.get(type);
    }

    @Getter
    @AllArgsConstructor
    public static class MatcherInfo {
        private final Class<?> matcher;
        private final boolean isBooleanType;
    }
}
