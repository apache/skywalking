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

package org.apache.skywalking.apm.plugin.spring.mvc.naming;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class PatternsRequestCondition {
    public static final Set<String> EMPTY_PATH_PATTERN = new HashSet<>(0);
    private Set<String> patterns;
    private PathMatcher pathMatcher;

    public PatternsRequestCondition(Set<String> basePath, Set<String> patterns) {
        pathMatcher = new AntPathMatcher();
        if (basePath == null || basePath.isEmpty()) {
            this.patterns = patterns;
            return;
        }
        //org.springframework.web.servlet.mvc.condition.PatternsRequestCondition.combine
        Set<String> result = new LinkedHashSet<>();
        for (String pattern1 : basePath) {
            for (String pattern2 : patterns) {
                result.add(this.pathMatcher.combine(pattern1, pattern2));
            }
        }
        this.patterns = result;
    }

    public String match(String lookupPath) {
        for (String pattern : patterns) {
            if (pattern.equals(lookupPath)) {
                return pattern;
            }
            if (this.pathMatcher.match(pattern, lookupPath)) {
                return pattern;
            }
            if (!pattern.endsWith("/") && this.pathMatcher.match(pattern + "/", lookupPath)) {
                return pattern + "/";
            }
        }
        return null;
    }

    public Set<String> getDirectPaths() {
        if (patterns == null || patterns.isEmpty()) {
            return EMPTY_PATH_PATTERN;
        }
        Set<String> result = Collections.emptySet();
        for (String pattern : this.patterns) {
            if (!this.pathMatcher.isPattern(pattern)) {
                result = result.isEmpty() ? new HashSet<>(1) : result;
                result.add(pattern);
            }
        }
        return result;
    }
}
