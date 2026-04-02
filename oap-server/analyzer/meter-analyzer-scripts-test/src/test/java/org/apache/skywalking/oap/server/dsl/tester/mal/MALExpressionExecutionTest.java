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

package org.apache.skywalking.oap.server.dsl.tester.mal;

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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableMap;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.server.testing.dsl.mal.MalRuleLoader;
import org.apache.skywalking.oap.server.testing.dsl.mal.MalTestRule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * v2-only execution test for MAL (Meter Analysis Language) expressions.
 * Compiles and executes all MAL rules using ANTLR4 + Javassist via {@link MALClassGenerator}.
 *
 * <p>When a companion {@code .data.yaml} file exists alongside a MAL YAML script,
 * it provides realistic mock data (sample names, labels, values) for runtime
 * execution and expected output validation.
 */
@Slf4j
class MALExpressionExecutionTest {

    private static MockedStatic<org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry> K8S_MOCK;

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
        final org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry mockV2K8s =
            Mockito.mock(org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry.class);
        Mockito.when(mockV2K8s.findServiceName(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(inv -> inv.<String>getArgument(1) + "." + inv.<String>getArgument(0));
        K8S_MOCK = Mockito.mockStatic(
            org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry.class);
        K8S_MOCK.when(
                org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry::getInstance)
            .thenReturn(mockV2K8s);
    }

    @AfterAll
    static void teardownK8sMocks() {
        if (K8S_MOCK != null) {
            K8S_MOCK.close();
        }
    }

    private static final Pattern TAG_EQUAL_PATTERN =
        Pattern.compile("\\.tagEqual\\s*\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)");

    private static final String[] HISTOGRAM_LE_VALUES =
        {"50", "100", "250", "500", "1000"};

    /** Advance by 2 s per call — must be &gt;1 s (for timeDiff/1000≥1) and &lt;15 s (smallest rate window). */
    private long timestampCounter = System.currentTimeMillis();

    @TestFactory
    Collection<DynamicTest> malExpressionsMatch() throws Exception {
        final List<DynamicTest> tests = new ArrayList<>();
        final Map<String, List<MalTestRule>> yamlRules = loadAllMalYamlFiles();

        // Compile v2 filter once per source file — generates .class into generated-classes/
        final java.util.Set<File> compiledFilters = new java.util.HashSet<>();

        for (final Map.Entry<String, List<MalTestRule>> entry : yamlRules.entrySet()) {
            final String yamlFile = entry.getKey();
            for (final MalTestRule rule : entry.getValue()) {
                // Compile v2 filter once per source file (generates .class file)
                if (rule.getFilter() != null && rule.getSourceFile() != null
                        && compiledFilters.add(rule.getSourceFile())) {
                    final int filterLine = findFilterLine(rule.getSourceFile());
                    final MALClassGenerator filterGen = new MALClassGenerator();
                    filterGen.setClassOutputDir(
                        DslClassOutput.checkerTestDir(rule.getSourceFile()));
                    filterGen.setClassNameHint("filter");
                    filterGen.setYamlSource(filterLine > 0
                        ? rule.getSourceFile().getName() + ":" + filterLine
                        : rule.getSourceFile().getName());
                    filterGen.compileFilter(rule.getFilter());
                }

                // Compile v2 once per metric — compilation is independent of input data
                org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression v2Expr = null;
                ExpressionMetadata v2Meta = null;
                String v2CompileError = null;
                try {
                    v2Expr = compileV2(rule);
                    v2Meta = v2Expr.metadata();
                } catch (Exception e) {
                    v2CompileError = e.getMessage();
                }

                final org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression fExpr = v2Expr;
                final ExpressionMetadata fMeta = v2Meta;
                final String fErr = v2CompileError;
                tests.add(DynamicTest.dynamicTest(
                    yamlFile + " | " + rule.getName(),
                    () -> executeExpression(rule, fExpr, fMeta, fErr)
                ));
            }
        }

        return tests;
    }

    private org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression compileV2(
            final MalTestRule rule) throws Exception {
        final MALClassGenerator generator = new MALClassGenerator();
        if (rule.getSourceFile() != null) {
            generator.setClassOutputDir(
                DslClassOutput.checkerTestDir(rule.getSourceFile()));
            generator.setClassNameHint(rule.getName());
            generator.setYamlSource(rule.getLineNo() > 0
                ? rule.getSourceFile().getName() + ":" + rule.getLineNo()
                : rule.getSourceFile().getName());
        }
        return generator.compile(rule.getName(), rule.getFullExpression());
    }

    @SuppressWarnings("unchecked")
    private void executeExpression(
            final MalTestRule rule,
            final org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression v2MalExpr,
            final ExpressionMetadata v2Meta,
            final String v2CompileError) throws Exception {
        final String metricName = rule.getName();

        if (v2Meta == null) {
            fail(metricName + ": v2 compile failed — " + v2CompileError);
            return;
        }

        // ---- Runtime execution ----
        if (rule.getInputConfig() != null) {
            final Map<String, Object> inputSection =
                (Map<String, Object>) rule.getInputConfig().get("input");
            final Map<String, Object> expectedSection =
                (Map<String, Object>) rule.getInputConfig().get("expected");
            if (inputSection != null) {
                executeWithInput(
                    rule, v2MalExpr, v2Meta,
                    inputSection, expectedSection);
                return;
            }
        }
        executeWithAutoData(metricName, rule.getFullExpression(), v2MalExpr, v2Meta);
    }

    // ==================== Input-driven runtime execution ====================

    @SuppressWarnings("unchecked")
    private void executeWithInput(
            final MalTestRule rule,
            final org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression v2MalExpr,
            final ExpressionMetadata v2Meta,
            final Map<String, Object> inputSection,
            final Map<String, Object> expectedSection) {
        final String metricName = rule.getName();
        // Unique per file+rule to isolate CounterWindow entries across files
        final String cwMetricName = rule.getSourceFile().getName() + "/" + metricName;
        final String expression = rule.getFullExpression();
        final boolean hasIncrease = expression.contains(".increase(")
            || expression.contains(".rate(");

        // v2 prime + v2 real (also consecutive, same delta)
        final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> v2Data;
        if (hasIncrease) {
            try {
                final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> primeData =
                    buildV2MockDataFromInput(inputSection, 0.5);
                for (final org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily s : primeData.values()) {
                    if (s != org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily.EMPTY) {
                        s.context.setMetricName(cwMetricName);
                    }
                }
                v2MalExpr.run(primeData);
            } catch (Exception ignored) {
            }
        }
        v2Data = buildV2MockDataFromInput(inputSection, 1.0);
        for (final org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily s : v2Data.values()) {
            if (s != org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily.EMPTY) {
                s.context.setMetricName(cwMetricName);
            }
        }

        // V2 run
        org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily v2Sf;
        try {
            v2Sf = v2MalExpr.run(v2Data);
        } catch (Exception e) {
            fail(metricName + ": v2 runtime failed (with input data) — "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        // Must succeed
        final boolean v2Success = v2Sf != null
            && v2Sf != org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily.EMPTY;
        assertTrue(v2Success,
            metricName + ": v2 returned EMPTY");

        // Validate expected section
        if (expectedSection != null) {
            final String qualifiedMetricName = rule.getMetricPrefix() != null
                ? rule.getMetricPrefix() + "_" + metricName : metricName;
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
                .map(MALExpressionExecutionTest::describeEntity)
                .sorted()
                .collect(Collectors.toList());

            final List<String> expectedEntityDescs = expectedEntities.stream()
                .map(MALExpressionExecutionTest::describeExpectedEntity)
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

            // Sort expected by normalized (all-String) labels for consistent comparison
            // SnakeYAML may parse label values as Integer/null, so normalize first
            final List<Map<String, Object>> sortedExpected = new ArrayList<>(expectedSamples);
            sortedExpected.sort((a, b) -> {
                final String aLabels = normalizeLabelsForSort(a.get("labels"));
                final String bLabels = normalizeLabelsForSort(b.get("labels"));
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
        for (int i = 0; i <= 5; i++) {
            final Object attr = entity.get("attr" + i);
            if (attr != null) {
                sb.append("|attr").append(i).append("=").append(attr);
            }
        }
        return sb.toString();
    }

    /**
     * Normalize a YAML labels map to a string with all values converted to String.
     * SnakeYAML may parse label values as Integer (e.g. status: 404) or null
     * (e.g. status: ) which would sort differently than "404" or "".
     */
    @SuppressWarnings("unchecked")
    private static String normalizeLabelsForSort(final Object rawLabels) {
        if (!(rawLabels instanceof Map)) {
            return String.valueOf(rawLabels);
        }
        final Map<String, String> normalized = new TreeMap<>();
        for (final Map.Entry<?, ?> e : ((Map<?, ?>) rawLabels).entrySet()) {
            normalized.put(
                String.valueOf(e.getKey()),
                e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }
        return normalized.toString();
    }

    // ==================== Build mock data from .data.yaml ====================

    @SuppressWarnings("unchecked")
    private Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> buildV2MockDataFromInput(
            final Map<String, Object> inputSection, final double valueScale) {
        final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> data =
            new HashMap<>();
        final long now = timestampCounter;
        timestampCounter += 2_000;

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
                final double value = ((Number) sampleDef.get("value")).doubleValue()
                    * valueScale;
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

    private void executeWithAutoData(
            final String metricName,
            final String expression,
            final org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression v2MalExpr,
            final ExpressionMetadata v2Meta) {
        final boolean hasIncrease = expression.contains(".increase(")
            || expression.contains(".rate(");

        // For increase()/rate(), prime then build real data consecutively
        // so that prime→real has a consistent 2 s delta.
        final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> v2Data;
        if (hasIncrease) {
            try {
                final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> primeData =
                    buildV2MockData(metricName, expression, v2Meta, 0.5);
                for (final org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily s : primeData.values()) {
                    if (s != org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily.EMPTY) {
                        s.context.setMetricName(metricName);
                    }
                }
                v2MalExpr.run(primeData);
            } catch (Exception ignored) {
            }
        }
        v2Data = buildV2MockData(metricName, expression, v2Meta, 1.0);

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
            fail(metricName + ": v2 runtime failed — "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        // Must succeed
        final boolean v2Success = v2Sf != null
            && v2Sf != org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily.EMPTY;
        assertTrue(v2Success,
            metricName + ": v2 returned EMPTY");
    }

    // ==================== V2 mock data (.v2. packages) ====================

    private Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> buildV2MockData(
            final String metricName, final String expression,
            final ExpressionMetadata meta, final double valueScale) {
        final Map<String, org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily> data =
            new HashMap<>();
        final long now = timestampCounter;
        timestampCounter += 2_000;
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
                data.put(sampleName, buildV2HistogramSamples(
                    sampleName, labels, now, valueScale));
            } else {
                final org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample sample =
                    org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample.builder()
                        .name(sampleName)
                        .labels(ImmutableMap.copyOf(labels))
                        .value(100.0 * valueScale)
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
            final long timestamp, final double valueScale) {
        final List<org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample> samples =
            new ArrayList<>();
        double cumulativeValue = 0;
        for (final String le : HISTOGRAM_LE_VALUES) {
            cumulativeValue += 10.0 * valueScale;
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

    // ==================== Helpers ====================

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
        appendAttr(sb, "attr0", entity.getAttr0());
        appendAttr(sb, "attr1", entity.getAttr1());
        appendAttr(sb, "attr2", entity.getAttr2());
        appendAttr(sb, "attr3", entity.getAttr3());
        appendAttr(sb, "attr4", entity.getAttr4());
        appendAttr(sb, "attr5", entity.getAttr5());
        return sb.toString();
    }

    private static void appendAttr(final StringBuilder sb,
                                    final String name, final String value) {
        if (value != null) {
            sb.append("|").append(name).append("=").append(value);
        }
    }

    private static org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[] sortV2Samples(
            final org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[] samples) {
        final org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample[] sorted =
            Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted, (a, b) -> normalizeLabelsForSort(a.getLabels()).compareTo(
            normalizeLabelsForSort(b.getLabels())));
        return sorted;
    }

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

    private Map<String, List<MalTestRule>> loadAllMalYamlFiles() throws Exception {
        final Path dataDir = Path.of("src/test/resources/scripts/mal");
        if (!Files.isDirectory(dataDir)) {
            return new LinkedHashMap<>();
        }
        return MalRuleLoader.loadFromDataFiles(dataDir);
    }

    /**
     * Find the 1-based line number of the {@code filter:} field in a YAML file.
     */
    private static int findFilterLine(final File yamlFile) {
        try {
            final String[] lines = Files.readString(yamlFile.toPath()).split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].trim().startsWith("filter:")) {
                    return i + 1;
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
