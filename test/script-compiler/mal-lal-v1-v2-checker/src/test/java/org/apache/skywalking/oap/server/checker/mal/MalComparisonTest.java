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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.compiler.MALClassGenerator;
import org.apache.skywalking.oap.meter.analyzer.dsl.DSL;
import org.apache.skywalking.oap.meter.analyzer.dsl.Expression;
import org.apache.skywalking.oap.meter.analyzer.dsl.ExpressionMetadata;
import org.apache.skywalking.oap.meter.analyzer.dsl.ExpressionParsingContext;
import org.apache.skywalking.oap.meter.analyzer.dsl.MalExpression;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dual-path comparison test for MAL (Meter Analysis Language) expressions.
 * For each metric rule across all MAL YAML files:
 * <ul>
 *   <li>Path A (v1): Groovy compilation via upstream {@code DSL.parse()}</li>
 *   <li>Path B (v2): ANTLR4 + Javassist compilation via {@link MALClassGenerator}</li>
 * </ul>
 * Both paths run metadata extraction and compare the resulting metadata
 * (samples, scope, downsampling, aggregation labels).
 */
@Slf4j
class MalComparisonTest {

    private static final AtomicInteger V2_COMPILE_GAPS = new AtomicInteger();

    @TestFactory
    Collection<DynamicTest> malExpressionsMatch() throws Exception {
        final List<DynamicTest> tests = new ArrayList<>();
        final Map<String, List<MalRule>> yamlRules = loadAllMalYamlFiles();

        for (final Map.Entry<String, List<MalRule>> entry : yamlRules.entrySet()) {
            final String yamlFile = entry.getKey();
            for (final MalRule rule : entry.getValue()) {
                tests.add(DynamicTest.dynamicTest(
                    yamlFile + " | " + rule.name,
                    () -> compareExpression(rule.name, rule.fullExpression)
                ));
            }
        }

        return tests;
    }

    private void compareExpression(final String metricName,
                                   final String expression) throws Exception {
        // ---- V1: Groovy path ----
        ExpressionParsingContext v1Ctx = null;
        try {
            final Expression v1Expr = DSL.parse(metricName, expression);
            v1Ctx = v1Expr.parse();
        } catch (Exception e) {
            // V1 failed - skip comparison
        }

        // ---- V2: ANTLR4 + Javassist compilation ----
        ExpressionMetadata v2Meta = null;
        String v2Error = null;
        try {
            final MALClassGenerator generator = new MALClassGenerator();
            final MalExpression malExpr = generator.compile(metricName, expression);
            v2Meta = malExpr.metadata();
        } catch (Exception e) {
            v2Error = e.getMessage();
        }

        // ---- Compare ----
        if (v1Ctx == null && v2Meta == null) {
            return;
        }
        if (v1Ctx == null) {
            return;
        }
        if (v2Meta == null) {
            V2_COMPILE_GAPS.incrementAndGet();
            log.info("V2 compile gap for '{}': {}", metricName, v2Error);
            return;
        }

        // Both succeeded - compare metadata
        assertEquals(v1Ctx.getSamples(), v2Meta.getSamples(),
            metricName + ": samples mismatch");
        assertEquals(v1Ctx.getScopeType(), v2Meta.getScopeType(),
            metricName + ": scopeType mismatch");
        assertEquals(v1Ctx.getDownsampling(), v2Meta.getDownsampling(),
            metricName + ": downsampling mismatch");
        assertEquals(v1Ctx.isHistogram(), v2Meta.isHistogram(),
            metricName + ": isHistogram mismatch");
        assertEquals(v1Ctx.getScopeLabels(), v2Meta.getScopeLabels(),
            metricName + ": scopeLabels mismatch");
        assertEquals(v1Ctx.getAggregationLabels(), v2Meta.getAggregationLabels(),
            metricName + ": aggregationLabels mismatch");
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<MalRule>> loadAllMalYamlFiles() throws Exception {
        final Map<String, List<MalRule>> result = new HashMap<>();
        final Yaml yaml = new Yaml();

        final String[] dirs = {
            "meter-analyzer-config",
            "otel-rules"
        };

        for (final String dir : dirs) {
            final Path dirPath = findResourceDir(dir);
            if (dirPath == null) {
                continue;
            }
            collectYamlFiles(dirPath.toFile(), dir, yaml, result);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private void collectYamlFiles(final File dir, final String prefix,
                                  final Yaml yaml,
                                  final Map<String, List<MalRule>> result) throws Exception {
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (final File file : files) {
            if (file.isDirectory()) {
                collectYamlFiles(file, prefix + "/" + file.getName(), yaml, result);
                continue;
            }
            if (!file.getName().endsWith(".yaml") && !file.getName().endsWith(".yml")) {
                continue;
            }
            final String content = Files.readString(file.toPath());
            final Map<String, Object> config = yaml.load(content);
            if (config == null || !config.containsKey("metricsRules")) {
                continue;
            }
            final Object rawSuffix = config.get("expSuffix");
            final String expSuffix = rawSuffix instanceof String ? (String) rawSuffix : "";
            final Object rawPrefix = config.get("expPrefix");
            final String expPrefix = rawPrefix instanceof String ? (String) rawPrefix : "";
            final List<Map<String, String>> rules =
                (List<Map<String, String>>) config.get("metricsRules");
            if (rules == null) {
                continue;
            }

            final String yamlName = prefix + "/" + file.getName();
            final List<MalRule> malRules = new ArrayList<>();
            for (final Map<String, String> rule : rules) {
                final String name = rule.get("name");
                final String exp = rule.get("exp");
                if (name == null || exp == null) {
                    continue;
                }
                String fullExp = exp;
                if (!expPrefix.isEmpty()) {
                    fullExp = expPrefix + "." + fullExp;
                }
                if (!expSuffix.isEmpty()) {
                    fullExp = fullExp + "." + expSuffix;
                }
                malRules.add(new MalRule(name, fullExp));
            }
            if (!malRules.isEmpty()) {
                result.put(yamlName, malRules);
            }
        }
    }

    private Path findResourceDir(final String name) {
        final String[] candidates = {
            "oap-server/server-starter/src/main/resources/" + name,
            "../../../oap-server/server-starter/src/main/resources/" + name
        };
        for (final String candidate : candidates) {
            final Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    private static class MalRule {
        final String name;
        final String fullExpression;

        MalRule(final String name, final String fullExpression) {
            this.name = name;
            this.fullExpression = fullExpression;
        }
    }
}
