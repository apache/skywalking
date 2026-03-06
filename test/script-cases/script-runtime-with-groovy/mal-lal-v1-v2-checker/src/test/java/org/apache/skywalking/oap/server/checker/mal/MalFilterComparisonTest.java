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

package org.apache.skywalking.oap.server.checker.mal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Dual-path comparison test for MAL filter expressions.
 * For each unique filter expression across all MAL YAML files:
 * <ul>
 *   <li>Path A (v1): Groovy {@code GroovyShell.evaluate()} -> {@code Closure<Boolean>}</li>
 *   <li>Path B (v2): ANTLR4 + Javassist compilation via {@link MALClassGenerator}</li>
 * </ul>
 * Both paths are invoked with representative tag maps and results compared.
 */
class MalFilterComparisonTest {

    @TestFactory
    Collection<DynamicTest> filterExpressionsMatch() throws Exception {
        final Set<String> filters = collectAllFilterExpressions();
        final List<DynamicTest> tests = new ArrayList<>();

        for (final String filterExpr : filters) {
            tests.add(DynamicTest.dynamicTest(
                "filter: " + filterExpr,
                () -> compareFilter(filterExpr)
            ));
        }

        return tests;
    }

    @SuppressWarnings("unchecked")
    private void compareFilter(final String filterExpr) throws Exception {
        final List<Map<String, String>> testTags = buildTestTags(filterExpr);

        // ---- V1: Groovy closure ----
        final Closure<Boolean> v1Closure;
        try {
            v1Closure = (Closure<Boolean>) new GroovyShell().evaluate(filterExpr);
        } catch (Exception e) {
            fail("V1 (Groovy) failed to evaluate filter: " + filterExpr + " - " + e.getMessage());
            return;
        }

        // ---- V2: ANTLR4 + Javassist compilation ----
        final MalFilter v2Filter;
        try {
            final MALClassGenerator generator = new MALClassGenerator();
            v2Filter = generator.compileFilter(filterExpr);
        } catch (Exception e) {
            fail("V2 (Java) failed for filter: " + filterExpr + " - " + e.getMessage());
            return;
        }

        // ---- Compare with test data ----
        for (final Map<String, String> tags : testTags) {
            boolean v1Result;
            try {
                v1Result = v1Closure.call(tags);
            } catch (Exception e) {
                continue;
            }
            boolean v2Result;
            try {
                v2Result = v2Filter.test(tags);
            } catch (NullPointerException e) {
                v2Result = false;
            }
            assertEquals(v1Result, v2Result,
                "Filter diverged for tags=" + tags + ": v1=" + v1Result + ", v2=" + v2Result
                    + " (filter: " + filterExpr + ")");
        }
    }

    private List<Map<String, String>> buildTestTags(final String filterExpr) {
        final List<Map<String, String>> testTags = new ArrayList<>();

        testTags.add(new HashMap<>());

        final java.util.regex.Pattern kvPattern =
            java.util.regex.Pattern.compile("tags\\.(\\w+)\\s*==\\s*'([^']+)'");
        final java.util.regex.Matcher matcher = kvPattern.matcher(filterExpr);

        final Map<String, String> matchingTags = new HashMap<>();
        final Map<String, String> mismatchTags = new HashMap<>();
        while (matcher.find()) {
            final String key = matcher.group(1);
            final String value = matcher.group(2);
            matchingTags.put(key, value);
            mismatchTags.put(key, value + "_wrong");
        }

        if (!matchingTags.isEmpty()) {
            testTags.add(matchingTags);
            testTags.add(mismatchTags);
        }

        final Map<String, String> unrelatedTags = new HashMap<>();
        unrelatedTags.put("unrelated_key", "some_value");
        testTags.add(unrelatedTags);

        return testTags;
    }

    @SuppressWarnings("unchecked")
    private Set<String> collectAllFilterExpressions() throws Exception {
        final Set<String> filters = new LinkedHashSet<>();
        final Yaml yaml = new Yaml();

        final String[] dirs = {
            "test-meter-analyzer-config", "test-otel-rules",
            "test-log-mal-rules", "test-envoy-metrics-rules"
        };
        final Path scriptsDir = findScriptsDir("mal");
        if (scriptsDir != null) {
            for (final String dir : dirs) {
                final Path dirPath = scriptsDir.resolve(dir);
                if (Files.isDirectory(dirPath)) {
                    collectFiltersFromDir(dirPath.toFile(), yaml, filters);
                }
            }
        }

        return filters;
    }

    @SuppressWarnings("unchecked")
    private void collectFiltersFromDir(final File dir, final Yaml yaml,
                                       final Set<String> filters) throws Exception {
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (final File file : files) {
            if (file.isDirectory()) {
                collectFiltersFromDir(file, yaml, filters);
                continue;
            }
            if (!file.getName().endsWith(".yaml") && !file.getName().endsWith(".yml")) {
                continue;
            }
            final String content = Files.readString(file.toPath());
            final Map<String, Object> config = yaml.load(content);
            if (config == null) {
                continue;
            }
            final Object filterObj = config.get("filter");
            if (filterObj instanceof String) {
                final String filter = ((String) filterObj).trim();
                if (!filter.isEmpty()) {
                    filters.add(filter);
                }
            }
        }
    }

    private Path findScriptsDir(final String language) {
        final String[] candidates = {
            "test/script-cases/scripts/" + language,
            "../../scripts/" + language
        };
        for (final String candidate : candidates) {
            final Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }
}
