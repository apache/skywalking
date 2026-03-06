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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.yaml.snakeyaml.Yaml;

/**
 * Generates rich {@code expected:} sections in companion {@code .data.yaml} files
 * by running v1 (Groovy) MAL expressions and capturing their output.
 *
 * <p>v1 is production-verified and trusted. Its output (entities, samples, values)
 * becomes the expected baseline for v1-v2 comparison tests.
 *
 * <p>Run via {@link MalExpectedDataGeneratorTest}.
 */
@Slf4j
public final class MalExpectedDataGenerator {

    private static final String[] DIRS = {
        "test-meter-analyzer-config",
        "test-otel-rules",
        "test-envoy-metrics-rules",
        "test-log-mal-rules",
        "test-telegraf-rules",
        "test-zabbix-rules"
    };

    /** Advance by 2 s per call — must be &gt;1 s (for timeDiff/1000≥1) and &lt;15 s (smallest rate window). */
    private long timestampCounter = System.currentTimeMillis();
    private MockedStatic<org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry> v1K8sMock;
    private MockedStatic<org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry> v2K8sMock;

    static {
        final org.apache.skywalking.oap.server.core.config.NamingControl namingControl =
            Mockito.mock(org.apache.skywalking.oap.server.core.config.NamingControl.class);
        Mockito.when(namingControl.formatServiceName(
                org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(namingControl.formatInstanceName(
                org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(namingControl.formatEndpointName(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        MeterEntity.setNamingControl(namingControl);
    }

    public void setupK8sMocks() {
        // Mock v1 K8sInfoRegistry
        final org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry mockV1 =
            Mockito.mock(org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry.class);
        Mockito.when(mockV1.findServiceName(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(inv -> {
                final String ns = inv.getArgument(0);
                final String pod = inv.getArgument(1);
                return pod + "." + ns;
            });
        v1K8sMock = Mockito.mockStatic(
            org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry.class);
        v1K8sMock.when(
                org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry::getInstance)
            .thenReturn(mockV1);

        // Mock v2 K8sInfoRegistry
        final org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry mockV2 =
            Mockito.mock(org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry.class);
        Mockito.when(mockV2.findServiceName(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(inv -> {
                final String ns = inv.getArgument(0);
                final String pod = inv.getArgument(1);
                return pod + "." + ns;
            });
        v2K8sMock = Mockito.mockStatic(
            org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry.class);
        v2K8sMock.when(
                org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry::getInstance)
            .thenReturn(mockV2);
    }

    public void teardownK8sMocks() {
        if (v1K8sMock != null) {
            v1K8sMock.close();
        }
        if (v2K8sMock != null) {
            v2K8sMock.close();
        }
    }

    /**
     * Process all MAL script directories and update expected sections in .data.yaml files.
     *
     * @return int[3]: [updated, skipped, errors]
     */
    public int[] processAll() throws Exception {
        final Path scriptsDir = findScriptsDir();
        if (scriptsDir == null) {
            log.warn("Cannot find scripts/mal directory");
            return new int[]{0, 0, 0};
        }
        int updated = 0;
        int skipped = 0;
        int errors = 0;
        for (final String dir : DIRS) {
            final Path dirPath = scriptsDir.resolve(dir);
            if (Files.isDirectory(dirPath)) {
                final int[] counts = processDirectory(dirPath);
                updated += counts[0];
                skipped += counts[1];
                errors += counts[2];
            }
        }
        log.info("Expected generation: updated={}, skipped={}, errors={}", updated, skipped, errors);
        return new int[]{updated, skipped, errors};
    }

    @SuppressWarnings("unchecked")
    int[] processDirectory(final Path dir) throws Exception {
        int updated = 0;
        int skipped = 0;
        int errors = 0;
        final File[] files = dir.toFile().listFiles();
        if (files == null) {
            return new int[]{0, 0, 0};
        }
        for (final File file : files) {
            if (file.isDirectory()) {
                final int[] sub = processDirectory(file.toPath());
                updated += sub[0];
                skipped += sub[1];
                errors += sub[2];
                continue;
            }
            if (!file.getName().endsWith(".yaml") && !file.getName().endsWith(".yml")) {
                continue;
            }
            if (file.getName().endsWith(".data.yaml") || file.getName().endsWith(".data.yml")) {
                continue;
            }
            final String baseName = file.getName().replaceAll("\\.(yaml|yml)$", "");
            final File dataFile = new File(file.getParentFile(), baseName + ".data.yaml");
            if (!dataFile.exists()) {
                skipped++;
                continue;
            }
            try {
                if (generateExpectedForFile(file, dataFile)) {
                    updated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("Error processing {}: {}", file.getName(), e.getMessage());
                errors++;
            }
        }
        return new int[]{updated, skipped, errors};
    }

    @SuppressWarnings("unchecked")
    boolean generateExpectedForFile(final File yamlFile, final File dataFile)
            throws IOException {
        final Yaml yaml = new Yaml();
        final Map<String, Object> config = yaml.load(Files.readString(yamlFile.toPath()));
        if (config == null
            || (!config.containsKey("metricsRules") && !config.containsKey("metrics"))) {
            return false;
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
        if (rules == null || rules.isEmpty()) {
            return false;
        }

        // Load input section from data file
        final Map<String, Object> dataConfig = yaml.load(Files.readString(dataFile.toPath()));
        if (dataConfig == null) {
            return false;
        }
        final Map<String, Object> inputSection =
            (Map<String, Object>) dataConfig.get("input");
        if (inputSection == null) {
            return false;
        }

        // Run v1 for each rule and collect expected output
        final Map<String, ExpectedOutput> expectations = new LinkedHashMap<>();
        boolean anyChanged = false;
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
            final String qualifiedName = metricPrefix != null
                ? metricPrefix + "_" + uniqueName : uniqueName;
            final String fullExp = formatExp(expPrefix, expSuffix, exp);
            final boolean hasIncrease = fullExp.contains(".increase(")
                || fullExp.contains(".rate(");

            try {
                final org.apache.skywalking.oap.meter.analyzer.dsl.Expression v1Expr =
                    org.apache.skywalking.oap.meter.analyzer.dsl.DSL.parse(name, fullExp);

                // Clear CounterWindow before each rule so previous rules' entries
                // cannot contaminate rate()/increase() calculations
                org.apache.skywalking.oap.meter.analyzer.dsl.counter.CounterWindow
                    .INSTANCE.reset();

                // Unique metricName per file+rule to isolate CounterWindow entries
                final String cwMetricName = yamlFile.getName() + "/" + name;

                // Prime for increase()/rate() with half-values FIRST (older timestamp)
                // so rate = (value - value*0.5) / dt ≠ 0
                if (hasIncrease) {
                    try {
                        v1Expr.run(buildV1MockDataFromInput(inputSection, 0.5, cwMetricName));
                    } catch (Exception ignored) {
                    }
                }

                // Build v1 mock data from input (full values, newer timestamp)
                final Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> v1Data =
                    buildV1MockDataFromInput(inputSection, 1.0, cwMetricName);

                // Run v1
                final org.apache.skywalking.oap.meter.analyzer.dsl.Result v1Result =
                    v1Expr.run(v1Data);

                if (!v1Result.isSuccess()) {
                    throw new IllegalStateException(
                        "v1 returned not-success — fix input data in "
                            + dataFile.getName());
                }

                final org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily sf =
                    v1Result.getData();
                if (sf == null
                        || sf == org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily.EMPTY
                        || sf.samples.length == 0) {
                    log.warn("  {} [{}]: v1 returned EMPTY", yamlFile.getName(), name);
                    expectations.put(qualifiedName, ExpectedOutput.empty());
                    continue;
                }

                // Capture entities
                final List<EntityInfo> entities = new ArrayList<>();
                for (final MeterEntity entity : sf.context.getMeterSamples().keySet()) {
                    entities.add(new EntityInfo(
                        entity.getScopeType().name(),
                        entity.getServiceName(),
                        entity.getInstanceName(),
                        entity.getEndpointName(),
                        entity.getLayer() != null ? entity.getLayer().name() : null,
                        new String[] {
                            entity.getAttr0(), entity.getAttr1(), entity.getAttr2(),
                            entity.getAttr3(), entity.getAttr4(), entity.getAttr5()
                        }
                    ));
                }

                // Capture samples (sorted for deterministic output)
                final org.apache.skywalking.oap.meter.analyzer.dsl.Sample[] sorted =
                    Arrays.copyOf(sf.samples, sf.samples.length);
                Arrays.sort(sorted, (a, b) ->
                    a.getLabels().toString().compareTo(b.getLabels().toString()));

                final List<SampleInfo> samples = new ArrayList<>();
                for (final org.apache.skywalking.oap.meter.analyzer.dsl.Sample s : sorted) {
                    samples.add(new SampleInfo(
                        new LinkedHashMap<>(s.getLabels()),
                        s.getValue()));
                }

                expectations.put(qualifiedName, new ExpectedOutput(entities, samples));
                anyChanged = true;

            } catch (Exception e) {
                log.warn("  {} [{}]: v1 failed — {}: {}",
                    yamlFile.getName(), name,
                    e.getClass().getSimpleName(), e.getMessage());
                expectations.put(qualifiedName, ExpectedOutput.error(e.getMessage()));
            }
        }

        if (!anyChanged && expectations.values().stream().allMatch(e -> e.samples == null)) {
            return false;
        }

        // Rewrite the data file: keep input section, replace expected section
        rewriteDataFile(dataFile, dataConfig, expectations);
        log.info("  Updated expected: {}", dataFile.getName());
        return true;
    }

    @SuppressWarnings("unchecked")
    private void rewriteDataFile(final File dataFile,
                                 final Map<String, Object> dataConfig,
                                 final Map<String, ExpectedOutput> expectations)
            throws IOException {
        // Read original file to preserve input section exactly as-is
        final String original = Files.readString(dataFile.toPath());
        final int expectedIdx = original.indexOf("\nexpected:");
        final String inputPart;
        if (expectedIdx >= 0) {
            inputPart = original.substring(0, expectedIdx + 1);
        } else {
            inputPart = original + "\n";
        }

        final StringBuilder sb = new StringBuilder(inputPart);
        sb.append("expected:\n");
        for (final Map.Entry<String, ExpectedOutput> entry : expectations.entrySet()) {
            final String metricName = entry.getKey();
            final ExpectedOutput output = entry.getValue();
            sb.append("  ").append(yamlKey(metricName)).append(":\n");

            if (output.error != null) {
                sb.append("    error: '").append(escapeYaml(output.error)).append("'\n");
                continue;
            }
            if (output.samples == null || output.samples.isEmpty()) {
                sb.append("    empty: true\n");
                continue;
            }

            // Entities
            if (!output.entities.isEmpty()) {
                sb.append("    entities:\n");
                for (final EntityInfo e : output.entities) {
                    sb.append("      - scope: ").append(e.scope).append("\n");
                    if (e.service != null && !e.service.isEmpty()) {
                        sb.append("        service: ").append(yamlValue(e.service)).append("\n");
                    }
                    if (e.instance != null && !e.instance.isEmpty()) {
                        sb.append("        instance: ").append(yamlValue(e.instance)).append("\n");
                    }
                    if (e.endpoint != null && !e.endpoint.isEmpty()) {
                        sb.append("        endpoint: ").append(yamlValue(e.endpoint)).append("\n");
                    }
                    if (e.layer != null) {
                        sb.append("        layer: ").append(e.layer).append("\n");
                    }
                    for (int ai = 0; ai < e.attrs.length; ai++) {
                        if (e.attrs[ai] != null) {
                            sb.append("        attr").append(ai).append(": ")
                                .append(yamlValue(e.attrs[ai])).append("\n");
                        }
                    }
                }
            }

            // Samples
            sb.append("    samples:\n");
            for (final SampleInfo s : output.samples) {
                sb.append("      - labels:\n");
                for (final Map.Entry<String, String> l : s.labels.entrySet()) {
                    sb.append("          ").append(yamlKey(l.getKey()))
                        .append(": ").append(yamlValue(l.getValue())).append("\n");
                }
                sb.append("        value: ").append(s.value).append("\n");
            }
        }

        Files.writeString(dataFile.toPath(), sb.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily>
            buildV1MockDataFromInput(final Map<String, Object> inputSection,
                                     final double valueScale,
                                     final String metricName) {
        final Map<String, org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily> data =
            new HashMap<>();
        final long now = timestampCounter;
        timestampCounter += 2_000;

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
                final double value = ((Number) sampleDef.get("value")).doubleValue()
                    * valueScale;
                samples.add(org.apache.skywalking.oap.meter.analyzer.dsl.Sample.builder()
                    .name(sampleName)
                    .labels(ImmutableMap.copyOf(labels))
                    .value(value)
                    .timestamp(now)
                    .build());
            }

            final org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily sf =
                org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder
                    .newBuilder(samples.toArray(
                        new org.apache.skywalking.oap.meter.analyzer.dsl.Sample[0]))
                    .build();
            sf.context.setMetricName(metricName);
            data.put(sampleName, sf);
        }
        return data;
    }

    static String formatExp(final String expPrefix, final String expSuffix,
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

    private static String yamlKey(final String key) {
        if (key.contains("-") || key.contains(".") || key.contains(" ")) {
            return "'" + key + "'";
        }
        return key;
    }

    private static String yamlValue(final String value) {
        if (value == null) {
            return "''";
        }
        if (value.contains(":") || value.contains("#") || value.contains("{")
            || value.contains("}") || value.contains("[") || value.contains("]")
            || value.contains("'") || value.contains("\"") || value.contains(",")
            || value.contains("&") || value.contains("*") || value.contains("!")
            || value.contains("|") || value.contains(">") || value.contains("%")
            || value.contains("@") || value.contains("`")
            || "true".equals(value) || "false".equals(value)
            || "null".equals(value) || "yes".equals(value) || "no".equals(value)
            || "-".equals(value) || "~".equals(value)
            || value.startsWith("- ") || value.startsWith("? ")) {
            return "'" + value.replace("'", "''") + "'";
        }
        // Quote numeric strings so SnakeYAML doesn't parse them as Integer/Double
        try {
            Double.parseDouble(value);
            return "'" + value + "'";
        } catch (NumberFormatException ignored) {
        }
        return value;
    }

    private static String escapeYaml(final String s) {
        if (s == null) {
            return "";
        }
        return s.replace("'", "''").replace("\n", " ");
    }

    Path findScriptsDir() {
        final String[] candidates = {
            "test/script-cases/scripts/mal",
            "../../scripts/mal"
        };
        for (final String candidate : candidates) {
            final Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    static final class ExpectedOutput {
        final List<EntityInfo> entities;
        final List<SampleInfo> samples;
        final String error;

        ExpectedOutput(final List<EntityInfo> entities, final List<SampleInfo> samples) {
            this.entities = entities;
            this.samples = samples;
            this.error = null;
        }

        private ExpectedOutput(final String error, final boolean isEmpty) {
            this.entities = null;
            this.samples = null;
            this.error = isEmpty ? null : error;
        }

        static ExpectedOutput error(final String message) {
            return new ExpectedOutput(message, false);
        }

        static ExpectedOutput empty() {
            return new ExpectedOutput(null, true);
        }
    }

    static final class EntityInfo {
        final String scope;
        final String service;
        final String instance;
        final String endpoint;
        final String layer;
        final String[] attrs;

        EntityInfo(final String scope, final String service, final String instance,
                   final String endpoint, final String layer, final String[] attrs) {
            this.scope = scope;
            this.service = service;
            this.instance = instance;
            this.endpoint = endpoint;
            this.layer = layer;
            this.attrs = attrs;
        }
    }

    static final class SampleInfo {
        final Map<String, String> labels;
        final double value;

        SampleInfo(final Map<String, String> labels, final double value) {
            this.labels = labels;
            this.value = value;
        }
    }
}
