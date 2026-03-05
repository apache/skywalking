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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableMap;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Dual-path comparison test for MAL (Meter Analysis Language) expressions.
 * <ul>
 *   <li>Path A (v1): Groovy via {@code org.apache.skywalking.oap.meter.analyzer.dsl.DSL}</li>
 *   <li>Path B (v2): ANTLR4 + Javassist via {@link MALClassGenerator}</li>
 * </ul>
 *
 * <p>When a companion {@code .data.yaml} file exists alongside a MAL YAML script,
 * it provides realistic mock data (sample names, labels, values) for runtime
 * execution comparison and expected output validation.
 *
 * <p>v1 classes use original package {@code org.apache.skywalking.oap.meter.analyzer.dsl.*},
 * v2 classes use {@code org.apache.skywalking.oap.meter.analyzer.v2.dsl.*}.
 * Both are called via hard-coded typed references (no reflection).
 */
@Slf4j
class MalComparisonTest {

    private static MockedStatic<org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry> V1_K8S_MOCK;
    private static MockedStatic<org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry> V2_K8S_MOCK;

    static {
        final org.apache.skywalking.oap.server.core.config.NamingControl namingControl =
            Mockito.mock(org.apache.skywalking.oap.server.core.config.NamingControl.class);
        Mockito.when(namingControl.formatServiceName(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(namingControl.formatInstanceName(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(namingControl.formatEndpointName(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        MeterEntity.setNamingControl(namingControl);

        // Mock K8s metadata for retagByK8sMeta rules (pod→service lookup)
        final org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry mockV1K8s =
            Mockito.mock(org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry.class);
        Mockito.when(mockV1K8s.findServiceName(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(inv -> inv.<String>getArgument(1) + "." + inv.<String>getArgument(0));
        V1_K8S_MOCK = Mockito.mockStatic(
            org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry.class);
        V1_K8S_MOCK.when(
                org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry::getInstance)
            .thenReturn(mockV1K8s);

        final org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry mockV2K8s =
            Mockito.mock(org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry.class);
        Mockito.when(mockV2K8s.findServiceName(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(inv -> inv.<String>getArgument(1) + "." + inv.<String>getArgument(0));
        V2_K8S_MOCK = Mockito.mockStatic(
            org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry.class);
        V2_K8S_MOCK.when(
                org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry::getInstance)
            .thenReturn(mockV2K8s);
    }

    @AfterAll
    static void teardownK8sMocks() {
        if (V1_K8S_MOCK != null) {
            V1_K8S_MOCK.close();
        }
        if (V2_K8S_MOCK != null) {
            V2_K8S_MOCK.close();
        }
    }

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
                    () -> compareExpression(rule)
                ));
            }
        }

        return tests;
    }

    @SuppressWarnings("unchecked")
    private void compareExpression(final MalRule rule) throws Exception {
        final String metricName = rule.name;
        final String expression = rule.fullExpression;

        // ---- V1: Groovy path (original packages) ----
        final org.apache.skywalking.oap.meter.analyzer.dsl.Expression v1Expr;
        final org.apache.skywalking.oap.meter.analyzer.dsl.ExpressionParsingContext v1Ctx;
        try {
            v1Expr = org.apache.skywalking.oap.meter.analyzer.dsl.DSL.parse(
                metricName, expression);
            v1Ctx = v1Expr.parse();
        } catch (Exception e) {
            final Throwable cause = e.getCause() != null ? e.getCause() : e;
            fail(metricName + ": v1 (Groovy) failed — "
                + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            return;
        }

        // ---- V2: ANTLR4 + Javassist (.v2. packages) ----
        org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression v2MalExpr = null;
        ExpressionMetadata v2Meta = null;
        String v2Error = null;
        try {
            final MALClassGenerator generator = new MALClassGenerator();
            if (rule.sourceFile != null) {
                final String baseName = rule.sourceFile.getName()
                    .replaceFirst("\\.(yaml|yml)$", "");
                generator.setClassOutputDir(new java.io.File(
                    rule.sourceFile.getParent(),
                    baseName + ".generated-classes"));
                generator.setClassNameHint(metricName);
            }
            v2MalExpr = generator.compile(metricName, expression);
            v2Meta = v2MalExpr.metadata();
        } catch (Exception e) {
            v2Error = e.getMessage();
        }

        // ---- Compare metadata ----
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
        if (rule.inputConfig != null) {
            final Map<String, Object> inputSection =
                (Map<String, Object>) rule.inputConfig.get("input");
            final Map<String, Object> expectedSection =
                (Map<String, Object>) rule.inputConfig.get("expected");
            if (inputSection != null) {
                compareExecutionWithInput(
                    rule, v1Expr, v2MalExpr, v2Meta, inputSection, expectedSection);
                return;
            }
        }
        compareExecution(metricName, expression, v1Expr, v2MalExpr, v2Meta);
    }

    // ==================== Input-driven runtime comparison ====================

    @SuppressWarnings("unchecked")
    private void compareExecutionWithInput(
            final MalRule rule,
            final org.apache.skywalking.oap.meter.analyzer.dsl.Expression v1Expr,
            final org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression v2MalExpr,
            final ExpressionMetadata v2Meta,
            final Map<String, Object> inputSection,
            final Map<String, Object> expectedSection) {
        final String metricName = rule.name;
        final String expression = rule.fullExpression;
        final boolean hasIncrease = expression.contains(".increase(")
            || expression.contains(".rate(");

        // For increase()/rate(), prime the CounterWindow with initial data
        if (hasIncrease) {
            try {
                v1Expr.run(buildV1MockDataFromInput(inputSection));
            } catch (Exception ignored) {
            }
            try {
                final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> primeData =
                    buildV2MockDataFromInput(inputSection);
                for (final org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily s : primeData.values()) {
                    if (s != org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily.EMPTY) {
                        s.context.setMetricName(metricName);
                    }
                }
                v2MalExpr.run(primeData);
            } catch (Exception ignored) {
            }
        }

        // Build mock data from input YAML
        final Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> v1Data =
            buildV1MockDataFromInput(inputSection);
        final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> v2Data =
            buildV2MockDataFromInput(inputSection);

        // V1 run — v1 is production-verified; if it fails, the test data is wrong
        org.apache.skywalking.oap.meter.analyzer.dsl.Result v1Result;
        try {
            v1Result = v1Expr.run(v1Data);
        } catch (Exception e) {
            fail(metricName + ": v1 runtime failed with input data — "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
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
                fail(metricName + ": v2 runtime failed but v1 succeeded (with input data) — "
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

        // Validate expected section
        if (expectedSection != null) {
            final String qualifiedMetricName = rule.metricPrefix != null
                ? rule.metricPrefix + "_" + metricName : metricName;
            final Map<String, Object> metricExpected =
                (Map<String, Object>) expectedSection.get(qualifiedMetricName);
            if (metricExpected == null) {
                // Try without prefix
                final Map<String, Object> directExpected =
                    (Map<String, Object>) expectedSection.get(metricName);
                if (directExpected != null) {
                    validateExpected(metricName, v2Sf, v2Success, directExpected);
                }
            } else {
                validateExpected(qualifiedMetricName, v2Sf, v2Success, metricExpected);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateExpected(final String metricName,
                                  final org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily v2Sf,
                                  final boolean v2Success,
                                  final Map<String, Object> expected) {
        // Rules with error marker — v1 couldn't produce output; skip strict validation
        if (expected.containsKey("error")) {
            return;
        }

        // Rich expected: entities + samples → hard assertions
        final List<Map<String, Object>> expectedEntities =
            (List<Map<String, Object>>) expected.get("entities");
        final List<Map<String, Object>> expectedSamples =
            (List<Map<String, Object>>) expected.get("samples");

        if (expectedEntities != null || expectedSamples != null) {
            // EMPTY is a hard failure when rich expected data exists
            assertTrue(v2Success, metricName + ": v2 returned EMPTY but rich expected data exists");
            assertNotNull(v2Sf, metricName + ": v2 SampleFamily is null");
        }

        // Validate entities (MeterEntity from context)
        if (expectedEntities != null && !expectedEntities.isEmpty()) {
            final Map<MeterEntity, ?> meterSamples = v2Sf.context.getMeterSamples();
            assertNotNull(meterSamples, metricName + ": no MeterEntity output");

            final List<String> actualEntityDescs = meterSamples.keySet().stream()
                .map(MalComparisonTest::describeEntity)
                .sorted()
                .collect(Collectors.toList());

            final List<String> expectedEntityDescs = expectedEntities.stream()
                .map(MalComparisonTest::describeExpectedEntity)
                .sorted()
                .collect(Collectors.toList());

            assertEquals(expectedEntityDescs.size(), actualEntityDescs.size(),
                metricName + ": entity count mismatch — expected "
                    + expectedEntityDescs + " but got " + actualEntityDescs);

            for (int i = 0; i < expectedEntityDescs.size(); i++) {
                assertEquals(expectedEntityDescs.get(i), actualEntityDescs.get(i),
                    metricName + ": entity[" + i + "] mismatch");
            }
        }

        // Validate samples (labels + values)
        if (expectedSamples != null && !expectedSamples.isEmpty()) {
            final org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[] actualSorted =
                sortV2Samples(v2Sf.samples);

            assertEquals(expectedSamples.size(), actualSorted.length,
                metricName + ": expected " + expectedSamples.size()
                    + " samples but got " + actualSorted.length);

            // Sort expected by labels string for consistent comparison
            final List<Map<String, Object>> sortedExpected = new ArrayList<>(expectedSamples);
            sortedExpected.sort((a, b) -> {
                final String aLabels = String.valueOf(a.get("labels"));
                final String bLabels = String.valueOf(b.get("labels"));
                return aLabels.compareTo(bLabels);
            });

            for (int i = 0; i < sortedExpected.size(); i++) {
                final Map<String, Object> expSample = sortedExpected.get(i);
                final org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample actSample =
                    actualSorted[i];

                // Compare labels
                final Map<?, ?> rawExpLabels =
                    (Map<?, ?>) expSample.get("labels");
                if (rawExpLabels != null) {
                    final Map<String, String> expLabels = new LinkedHashMap<>();
                    for (final Map.Entry<?, ?> le : rawExpLabels.entrySet()) {
                        expLabels.put(String.valueOf(le.getKey()),
                            le.getValue() == null ? "" : String.valueOf(le.getValue()));
                    }
                    assertEquals(expLabels, actSample.getLabels(),
                        metricName + ": sample[" + i + "] labels mismatch");
                }

                // Compare values with tolerance
                // For time()-dependent expressions (large magnitudes), use relative tolerance
                if (expSample.containsKey("value")) {
                    final double expValue = ((Number) expSample.get("value")).doubleValue();
                    final double actValue = actSample.getValue();
                    final double tolerance = Math.abs(expValue) > 1e6
                        ? Math.abs(expValue) * 0.01 : 0.001;
                    assertEquals(expValue, actValue, tolerance,
                        metricName + ": sample[" + i + "] value mismatch"
                            + " (expected=" + expValue + ", actual=" + actValue + ")");
                }
            }
        }

        // Legacy min_samples (soft check for backwards compatibility)
        if (expected.containsKey("min_samples")) {
            final int minSamples = ((Number) expected.get("min_samples")).intValue();
            if (minSamples > 0 && v2Success) {
                assertTrue(v2Sf.samples.length >= minSamples,
                    metricName + ": expected min_samples=" + minSamples
                        + " but got " + v2Sf.samples.length);
            }
        }
    }

    private static String describeExpectedEntity(final Map<String, Object> entity) {
        final StringBuilder sb = new StringBuilder();
        sb.append(entity.getOrDefault("scope", "SERVICE"));
        sb.append("|svc=").append(entity.getOrDefault("service", ""));
        final Object inst = entity.get("instance");
        if (inst != null && !inst.toString().isEmpty()) {
            sb.append("|inst=").append(inst);
        }
        final Object ep = entity.get("endpoint");
        if (ep != null && !ep.toString().isEmpty()) {
            sb.append("|ep=").append(ep);
        }
        final Object layer = entity.get("layer");
        if (layer != null) {
            sb.append("|layer=").append(layer);
        }
        return sb.toString();
    }

    // ==================== Build mock data from .data.yaml ====================

    @SuppressWarnings("unchecked")
    private Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> buildV1MockDataFromInput(
            final Map<String, Object> inputSection) {
        final Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> data =
            new HashMap<>();
        final long now = timestampCounter++;

        for (final Map.Entry<String, Object> entry : inputSection.entrySet()) {
            final String sampleName = entry.getKey();
            final List<Map<String, Object>> sampleList =
                (List<Map<String, Object>>) entry.getValue();
            final List<org.apache.skywalking.oap.meter.analyzer.dsl.Sample> samples =
                new ArrayList<>();

            for (final Map<String, Object> sampleDef : sampleList) {
                final Map<String, String> labels = new HashMap<>();
                final Object rawLabels = sampleDef.get("labels");
                if (rawLabels instanceof Map) {
                    for (final Map.Entry<?, ?> le :
                            ((Map<?, ?>) rawLabels).entrySet()) {
                        labels.put(String.valueOf(le.getKey()),
                            le.getValue() == null ? "" : String.valueOf(le.getValue()));
                    }
                }
                final double value = ((Number) sampleDef.get("value")).doubleValue();
                samples.add(org.apache.skywalking.oap.meter.analyzer.dsl.Sample.builder()
                    .name(sampleName)
                    .labels(ImmutableMap.copyOf(labels))
                    .value(value)
                    .timestamp(now)
                    .build());
            }

            data.put(sampleName,
                org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder
                    .newBuilder(samples.toArray(
                        new org.apache.skywalking.oap.meter.analyzer.dsl.Sample[0]))
                    .build());
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> buildV2MockDataFromInput(
            final Map<String, Object> inputSection) {
        final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> data =
            new HashMap<>();
        final long now = timestampCounter++;

        for (final Map.Entry<String, Object> entry : inputSection.entrySet()) {
            final String sampleName = entry.getKey();
            final List<Map<String, Object>> sampleList =
                (List<Map<String, Object>>) entry.getValue();
            final List<org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample> samples =
                new ArrayList<>();

            for (final Map<String, Object> sampleDef : sampleList) {
                final Map<String, String> labels = new HashMap<>();
                final Object rawLabels = sampleDef.get("labels");
                if (rawLabels instanceof Map) {
                    for (final Map.Entry<?, ?> le :
                            ((Map<?, ?>) rawLabels).entrySet()) {
                        labels.put(String.valueOf(le.getKey()),
                            le.getValue() == null ? "" : String.valueOf(le.getValue()));
                    }
                }
                final double value = ((Number) sampleDef.get("value")).doubleValue();
                samples.add(org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample.builder()
                    .name(sampleName)
                    .labels(ImmutableMap.copyOf(labels))
                    .value(value)
                    .timestamp(now)
                    .build());
            }

            data.put(sampleName,
                org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder
                    .newBuilder(samples.toArray(
                        new org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[0]))
                    .build());
        }
        return data;
    }

    // ==================== Auto-generated mock data (fallback) ====================

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

        // V1 run — v1 is production-verified; if it fails, the mock data is wrong
        org.apache.skywalking.oap.meter.analyzer.dsl.Result v1Result;
        try {
            v1Result = v1Expr.run(v1Data);
        } catch (Exception e) {
            fail(metricName + ": v1 runtime failed with auto-generated data — "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
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
            for (final String label : meta.getScopeLabels()) {
                labels.put(label, inferLabelValue(label, tagEqualLabels));
            }
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
            for (final String label : meta.getScopeLabels()) {
                labels.put(label, inferLabelValue(label, tagEqualLabels));
            }
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

        // Compare MeterEntity output (service/instance/endpoint names)
        compareMeterEntities(metricName, v1Sf, v2Sf);
    }

    private static void compareMeterEntities(
            final String metricName,
            final org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily v1Sf,
            final org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily v2Sf) {
        final Map<MeterEntity, ?> v1Entities = v1Sf.context.getMeterSamples();
        final Map<MeterEntity, ?> v2Entities = v2Sf.context.getMeterSamples();

        if (v1Entities.isEmpty() && v2Entities.isEmpty()) {
            return;
        }

        assertEquals(v1Entities.size(), v2Entities.size(),
            metricName + ": MeterEntity count mismatch (v1="
                + v1Entities.size() + ", v2=" + v2Entities.size() + ")");

        final List<String> v1EntityDescs = v1Entities.keySet().stream()
            .map(e -> describeEntity(e))
            .sorted()
            .collect(Collectors.toList());
        final List<String> v2EntityDescs = v2Entities.keySet().stream()
            .map(e -> describeEntity(e))
            .sorted()
            .collect(Collectors.toList());

        assertEquals(v1EntityDescs, v2EntityDescs,
            metricName + ": MeterEntity mismatch");
    }

    private static String describeEntity(final MeterEntity entity) {
        final StringBuilder sb = new StringBuilder();
        sb.append(entity.getScopeType().name());
        final String svc = entity.getServiceName();
        sb.append("|svc=").append(svc == null ? "" : svc);
        if (entity.getInstanceName() != null && !entity.getInstanceName().isEmpty()) {
            sb.append("|inst=").append(entity.getInstanceName());
        }
        if (entity.getEndpointName() != null && !entity.getEndpointName().isEmpty()) {
            sb.append("|ep=").append(entity.getEndpointName());
        }
        if (entity.getLayer() != null) {
            sb.append("|layer=").append(entity.getLayer().name());
        }
        return sb.toString();
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
            "test-otel-rules",
            "test-envoy-metrics-rules",
            "test-log-mal-rules",
            "test-telegraf-rules",
            "test-zabbix-rules"
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
            // Skip companion .data.yaml files
            if (file.getName().endsWith(".data.yaml")) {
                continue;
            }
            final String content = Files.readString(file.toPath());
            final Map<String, Object> config = yaml.load(content);
            if (config == null
                || (!config.containsKey("metricsRules") && !config.containsKey("metrics"))) {
                continue;
            }
            final Object rawSuffix = config.get("expSuffix");
            final String expSuffix = rawSuffix instanceof String ? (String) rawSuffix : "";
            final Object rawPrefix = config.get("expPrefix");
            final String expPrefix = rawPrefix instanceof String ? (String) rawPrefix : "";
            final Object rawMetricPrefix = config.get("metricPrefix");
            final String metricPrefix = rawMetricPrefix instanceof String
                ? (String) rawMetricPrefix : null;
            // Support both "metricsRules" (standard) and "metrics" (zabbix)
            List<Map<String, String>> rules =
                (List<Map<String, String>>) config.get("metricsRules");
            if (rules == null) {
                rules = (List<Map<String, String>>) config.get("metrics");
            }
            if (rules == null) {
                continue;
            }

            // Load companion .data.yaml if it exists
            final String baseName = file.getName().replaceFirst("\\.(yaml|yml)$", "");
            final File inputFile = new File(file.getParent(), baseName + ".data.yaml");
            Map<String, Object> inputConfig = null;
            if (inputFile.exists()) {
                final String inputContent = Files.readString(inputFile.toPath());
                inputConfig = yaml.load(inputContent);
            }

            final String yamlName = prefix + "/" + file.getName();
            final List<MalRule> malRules = new ArrayList<>();
            final Map<String, Integer> nameCount = new HashMap<>();
            for (final Map<String, String> rule : rules) {
                final String name = rule.get("name");
                final String exp = rule.get("exp");
                if (name == null || exp == null) {
                    continue;
                }
                // Disambiguate duplicate rule names within the same file
                final int count = nameCount.merge(name, 1, Integer::sum);
                final String uniqueName = count > 1 ? name + "_" + count : name;
                final String fullExp = formatExp(expPrefix, expSuffix, exp);
                malRules.add(new MalRule(uniqueName, fullExp, inputConfig, metricPrefix, file));
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

    /**
     * Replicates the production {@code MetricConvert.formatExp()} logic:
     * inserts {@code expPrefix} after the metric name (first dot-segment),
     * and appends {@code expSuffix} after the whole expression.
     */
    private static String formatExp(final String expPrefix, final String expSuffix,
                                    final String exp) {
        String ret = exp;
        if (!expPrefix.isEmpty()) {
            final int dot = exp.indexOf('.');
            if (dot >= 0) {
                ret = String.format("(%s.%s)", exp.substring(0, dot), expPrefix);
                final String after = exp.substring(dot + 1);
                if (!after.isEmpty()) {
                    ret = String.format("(%s.%s)", ret, after);
                }
            } else {
                ret = String.format("(%s.%s)", exp, expPrefix);
            }
        }
        if (!expSuffix.isEmpty()) {
            ret = String.format("(%s).%s", ret, expSuffix);
        }
        return ret;
    }

    private static class MalRule {
        final String name;
        final String fullExpression;
        final Map<String, Object> inputConfig;
        final String metricPrefix;
        final File sourceFile;

        MalRule(final String name, final String fullExpression,
                final Map<String, Object> inputConfig, final String metricPrefix,
                final File sourceFile) {
            this.name = name;
            this.fullExpression = fullExpression;
            this.inputConfig = inputConfig;
            this.metricPrefix = metricPrefix;
            this.sourceFile = sourceFile;
        }
    }
}
