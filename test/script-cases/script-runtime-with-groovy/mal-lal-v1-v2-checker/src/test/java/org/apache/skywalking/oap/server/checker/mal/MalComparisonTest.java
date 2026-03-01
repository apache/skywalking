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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Dual-path comparison test for MAL (Meter Analysis Language) expressions.
 * <ul>
 *   <li>Path A (v1): Groovy via {@code org.apache.skywalking.oap.meter.analyzer.dsl.DSL}</li>
 *   <li>Path B (v2): ANTLR4 + Javassist via {@link MALClassGenerator}</li>
 * </ul>
 *
 * <p>v1 classes use original package {@code org.apache.skywalking.oap.meter.analyzer.dsl.*},
 * v2 classes use {@code org.apache.skywalking.oap.meter.analyzer.v2.dsl.*}.
 * Both are called via hard-coded typed references (no reflection).
 */
@Slf4j
class MalComparisonTest {

    private static final Pattern TAG_EQUAL_PATTERN =
        Pattern.compile("\\.tagEqual\\s*\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)");

    private static final String[] HISTOGRAM_LE_VALUES =
        {"50", "100", "250", "500", "1000"};

    private long timestampCounter = System.currentTimeMillis();

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
        // ---- V1: Groovy path (original packages) ----
        org.apache.skywalking.oap.meter.analyzer.dsl.Expression v1Expr = null;
        org.apache.skywalking.oap.meter.analyzer.dsl.ExpressionParsingContext v1Ctx = null;
        try {
            v1Expr = org.apache.skywalking.oap.meter.analyzer.dsl.DSL.parse(
                metricName, expression);
            v1Ctx = v1Expr.parse();
        } catch (Exception e) {
            // V1 failed - skip comparison
        }

        // ---- V2: ANTLR4 + Javassist (.v2. packages) ----
        org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression v2MalExpr = null;
        ExpressionMetadata v2Meta = null;
        String v2Error = null;
        try {
            final MALClassGenerator generator = new MALClassGenerator();
            v2MalExpr = generator.compile(metricName, expression);
            v2Meta = v2MalExpr.metadata();
        } catch (Exception e) {
            v2Error = e.getMessage();
        }

        // ---- Compare metadata ----
        if (v1Ctx == null && v2Meta == null) {
            return;
        }
        if (v1Ctx == null) {
            return;
        }
        if (v2Meta == null) {
            fail(metricName + ": v2 compile failed but v1 succeeded — " + v2Error);
            return;
        }

        assertEquals(v1Ctx.getSamples(), v2Meta.getSamples(),
            metricName + ": samples mismatch");
        assertEquals(v1Ctx.getScopeType(), v2Meta.getScopeType(),
            metricName + ": scopeType mismatch");
        assertEquals(
            v1Ctx.getDownsampling() == null ? null : v1Ctx.getDownsampling().name(),
            v2Meta.getDownsampling() == null ? null : v2Meta.getDownsampling().name(),
            metricName + ": downsampling mismatch");
        assertEquals(v1Ctx.isHistogram(), v2Meta.isHistogram(),
            metricName + ": isHistogram mismatch");
        assertEquals(v1Ctx.getScopeLabels(), v2Meta.getScopeLabels(),
            metricName + ": scopeLabels mismatch");
        assertEquals(v1Ctx.getAggregationLabels(), v2Meta.getAggregationLabels(),
            metricName + ": aggregationLabels mismatch");

        // ---- Runtime execution comparison ----
        compareExecution(metricName, expression, v1Expr, v2MalExpr, v2Meta);
    }

    private void compareExecution(
            final String metricName,
            final String expression,
            final org.apache.skywalking.oap.meter.analyzer.dsl.Expression v1Expr,
            final org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression v2MalExpr,
            final ExpressionMetadata v2Meta) {
        final boolean hasIncrease = expression.contains(".increase(")
            || expression.contains(".rate(");

        // For increase()/rate(), prime the CounterWindow with initial data
        if (hasIncrease) {
            try {
                v1Expr.run(buildV1MockData(metricName, expression, v2Meta));
            } catch (Exception ignored) {
            }
            try {
                final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> primeData =
                    buildV2MockData(metricName, expression, v2Meta);
                for (final org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily s : primeData.values()) {
                    if (s != org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily.EMPTY) {
                        s.context.setMetricName(metricName);
                    }
                }
                v2MalExpr.run(primeData);
            } catch (Exception ignored) {
            }
        }

        // Build fresh test data
        final Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> v1Data =
            buildV1MockData(metricName, expression, v2Meta);
        final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> v2Data =
            buildV2MockData(metricName, expression, v2Meta);

        // V1 run
        org.apache.skywalking.oap.meter.analyzer.dsl.Result v1Result;
        try {
            v1Result = v1Expr.run(v1Data);
        } catch (Exception e) {
            return;
        }

        // V2 run
        org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily v2Sf;
        try {
            for (final org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily s : v2Data.values()) {
                if (s != org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily.EMPTY) {
                    s.context.setMetricName(metricName);
                }
            }
            v2Sf = v2MalExpr.run(v2Data);
        } catch (Exception e) {
            if (v1Result.isSuccess()) {
                fail(metricName + ": v2 runtime failed but v1 succeeded — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            return;
        }

        // Compare results
        final boolean v2Success = v2Sf != null
            && v2Sf != org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily.EMPTY;
        assertEquals(v1Result.isSuccess(), v2Success,
            metricName + ": success mismatch (v1=" + v1Result.isSuccess()
                + ", v2=" + v2Success + ")");

        if (v1Result.isSuccess() && v2Success) {
            compareSampleFamilies(metricName, v1Result.getData(), v2Sf);
        }
    }

    // ==================== V1 mock data (original packages) ====================

    private Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> buildV1MockData(
            final String metricName, final String expression,
            final ExpressionMetadata meta) {
        final Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> data =
            new HashMap<>();
        final long now = timestampCounter++;
        final Map<String, String> tagEqualLabels = extractTagEqualLabels(expression);

        for (final String sampleName : meta.getSamples()) {
            final Map<String, String> labels = new HashMap<>();
            for (final String label : meta.getAggregationLabels()) {
                labels.put(label, inferLabelValue(label, tagEqualLabels));
            }
            labels.putAll(tagEqualLabels);

            if (meta.isHistogram()) {
                data.put(sampleName, buildV1HistogramSamples(sampleName, labels, now));
            } else {
                final org.apache.skywalking.oap.meter.analyzer.dsl.Sample sample =
                    org.apache.skywalking.oap.meter.analyzer.dsl.Sample.builder()
                        .name(sampleName)
                        .labels(ImmutableMap.copyOf(labels))
                        .value(100.0)
                        .timestamp(now)
                        .build();
                data.put(sampleName,
                    org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder
                        .newBuilder(sample).build());
            }
        }
        return data;
    }

    private org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily buildV1HistogramSamples(
            final String sampleName, final Map<String, String> baseLabels,
            final long timestamp) {
        final List<org.apache.skywalking.oap.meter.analyzer.dsl.Sample> samples =
            new ArrayList<>();
        double cumulativeValue = 0;
        for (final String le : HISTOGRAM_LE_VALUES) {
            cumulativeValue += 10.0;
            final Map<String, String> labels = new HashMap<>(baseLabels);
            labels.put("le", le);
            samples.add(org.apache.skywalking.oap.meter.analyzer.dsl.Sample.builder()
                .name(sampleName)
                .labels(ImmutableMap.copyOf(labels))
                .value(cumulativeValue)
                .timestamp(timestamp)
                .build());
        }
        return org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder.newBuilder(
            samples.toArray(new org.apache.skywalking.oap.meter.analyzer.dsl.Sample[0])).build();
    }

    // ==================== V2 mock data (.v2. packages) ====================

    private Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> buildV2MockData(
            final String metricName, final String expression,
            final ExpressionMetadata meta) {
        final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> data =
            new HashMap<>();
        final long now = timestampCounter++;
        final Map<String, String> tagEqualLabels = extractTagEqualLabels(expression);

        for (final String sampleName : meta.getSamples()) {
            final Map<String, String> labels = new HashMap<>();
            for (final String label : meta.getAggregationLabels()) {
                labels.put(label, inferLabelValue(label, tagEqualLabels));
            }
            labels.putAll(tagEqualLabels);

            if (meta.isHistogram()) {
                data.put(sampleName, buildV2HistogramSamples(sampleName, labels, now));
            } else {
                final org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample sample =
                    org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample.builder()
                        .name(sampleName)
                        .labels(ImmutableMap.copyOf(labels))
                        .value(100.0)
                        .timestamp(now)
                        .build();
                data.put(sampleName,
                    org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder
                        .newBuilder(sample).build());
            }
        }
        return data;
    }

    private org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily buildV2HistogramSamples(
            final String sampleName, final Map<String, String> baseLabels,
            final long timestamp) {
        final List<org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample> samples =
            new ArrayList<>();
        double cumulativeValue = 0;
        for (final String le : HISTOGRAM_LE_VALUES) {
            cumulativeValue += 10.0;
            final Map<String, String> labels = new HashMap<>(baseLabels);
            labels.put("le", le);
            samples.add(org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample.builder()
                .name(sampleName)
                .labels(ImmutableMap.copyOf(labels))
                .value(cumulativeValue)
                .timestamp(timestamp)
                .build());
        }
        return org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder.newBuilder(
            samples.toArray(
                new org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[0])).build();
    }

    // ==================== Cross-version comparison ====================

    private static void compareSampleFamilies(
            final String metricName,
            final org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily v1Sf,
            final org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily v2Sf) {
        final org.apache.skywalking.oap.meter.analyzer.dsl.Sample[] v1Sorted =
            sortV1Samples(v1Sf.samples);
        final org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[] v2Sorted =
            sortV2Samples(v2Sf.samples);

        assertEquals(v1Sorted.length, v2Sorted.length,
            metricName + ": output sample count mismatch (v1="
                + v1Sorted.length + ", v2=" + v2Sorted.length + ")");

        for (int i = 0; i < v1Sorted.length; i++) {
            assertEquals(v1Sorted[i].getLabels(), v2Sorted[i].getLabels(),
                metricName + ": output sample[" + i + "] labels mismatch");
            assertEquals(v1Sorted[i].getValue(), v2Sorted[i].getValue(), 0.001,
                metricName + ": output sample[" + i + "] value mismatch"
                    + " (v1=" + v1Sorted[i].getValue()
                    + ", v2=" + v2Sorted[i].getValue() + ")");
        }
    }

    private static org.apache.skywalking.oap.meter.analyzer.dsl.Sample[] sortV1Samples(
            final org.apache.skywalking.oap.meter.analyzer.dsl.Sample[] samples) {
        final org.apache.skywalking.oap.meter.analyzer.dsl.Sample[] sorted =
            Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted, (a, b) -> a.getLabels().toString().compareTo(
            b.getLabels().toString()));
        return sorted;
    }

    private static org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[] sortV2Samples(
            final org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[] samples) {
        final org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[] sorted =
            Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted, (a, b) -> a.getLabels().toString().compareTo(
            b.getLabels().toString()));
        return sorted;
    }

    // ==================== Helpers ====================

    private static Map<String, String> extractTagEqualLabels(final String expression) {
        final Map<String, String> labels = new HashMap<>();
        final Matcher matcher = TAG_EQUAL_PATTERN.matcher(expression);
        while (matcher.find()) {
            labels.put(matcher.group(1), matcher.group(2));
        }
        return labels;
    }

    private static String inferLabelValue(final String label,
                                          final Map<String, String> tagEqualLabels) {
        if (tagEqualLabels.containsKey(label)) {
            return tagEqualLabels.get(label);
        }
        switch (label) {
            case "service":
                return "test-service";
            case "instance":
            case "service_instance_id":
                return "test-instance";
            case "endpoint":
                return "/test";
            case "host_name":
                return "test-host";
            case "le":
                return "100";
            case "job_name":
                return "mysql-monitoring";
            case "cluster":
                return "test-cluster";
            case "node":
            case "node_id":
                return "test-node";
            case "topic":
                return "test-topic";
            case "queue":
                return "test-queue";
            case "broker":
                return "test-broker";
            default:
                return "test-value";
        }
    }

    // ==================== YAML loading ====================

    @SuppressWarnings("unchecked")
    private Map<String, List<MalRule>> loadAllMalYamlFiles() throws Exception {
        final Map<String, List<MalRule>> result = new HashMap<>();
        final Yaml yaml = new Yaml();

        final String[] dirs = {
            "test-meter-analyzer-config",
            "test-otel-rules"
        };

        final Path scriptsDir = findScriptsDir("mal");
        if (scriptsDir != null) {
            for (final String dir : dirs) {
                final Path dirPath = scriptsDir.resolve(dir);
                if (Files.isDirectory(dirPath)) {
                    collectYamlFiles(dirPath.toFile(), dir, yaml, result);
                }
            }
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

    private static class MalRule {
        final String name;
        final String fullExpression;

        MalRule(final String name, final String fullExpression) {
            this.name = name;
            this.fullExpression = fullExpression;
        }
    }
}
