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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

/**
 * Generates companion {@code .data.yaml} files for all MAL test YAML scripts.
 * Each generated file contains realistic mock input data (sample names, labels, values)
 * derived from compiling the MAL expression and parsing filter/tag constraints.
 *
 * <p>Run via: {@code main()} or as a JUnit test via {@link MalInputDataGeneratorTest}.
 */
@Slf4j
public final class MalInputDataGenerator {

    private static final String LICENSE_HEADER =
        "# Licensed to the Apache Software Foundation (ASF) under one or more\n"
            + "# contributor license agreements.  See the NOTICE file distributed with\n"
            + "# this work for additional information regarding copyright ownership.\n"
            + "# The ASF licenses this file to You under the Apache License, Version 2.0\n"
            + "# (the \"License\"); you may not use this file except in compliance with\n"
            + "# the License.  You may obtain a copy of the License at\n"
            + "#\n"
            + "#     http://www.apache.org/licenses/LICENSE-2.0\n"
            + "#\n"
            + "# Unless required by applicable law or agreed to in writing, software\n"
            + "# distributed under the License is distributed on an \"AS IS\" BASIS,\n"
            + "# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
            + "# See the License for the specific language governing permissions and\n"
            + "# limitations under the License.\n\n";

    private static final String[] HISTOGRAM_LE_VALUES =
        {"50", "100", "250", "500", "1000"};

    // Patterns for extracting constraints from MAL expressions
    private static final Pattern TAG_EQUAL_PATTERN =
        Pattern.compile("\\.tagEqual\\s*\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)");
    private static final Pattern TAG_NOT_EQUAL_NULL_PATTERN =
        Pattern.compile("\\.tagNotEqual\\s*\\(\\s*'([^']+)'\\s*,\\s*null\\s*\\)");
    private static final Pattern TAG_NOT_EQUAL_PATTERN =
        Pattern.compile("\\.tagNotEqual\\s*\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)");
    private static final Pattern TAG_MATCH_PATTERN =
        Pattern.compile("\\.tagMatch\\s*\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)");
    private static final Pattern TAG_NOT_MATCH_PATTERN =
        Pattern.compile("\\.tagNotMatch\\s*\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)");
    private static final Pattern CLOSURE_TAG_ACCESS_PATTERN =
        Pattern.compile("tags\\.([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern CLOSURE_TAG_BRACKET_PATTERN =
        Pattern.compile("tags\\['([^']+)'\\]");
    private static final Pattern VALUE_EQUAL_PATTERN =
        Pattern.compile("\\.valueEqual\\s*\\(\\s*([0-9.]+)\\s*\\)");
    // Extracts labels from entity functions: instance(['a'], ['b'], Layer.X)
    // Matches each ['label'] argument in service/instance/endpoint/process calls
    private static final Pattern ENTITY_FUNC_PATTERN =
        Pattern.compile("\\.(service|instance|endpoint|process|serviceRelation|processRelation)\\s*\\(");
    private static final Pattern STRING_LIST_ARG_PATTERN =
        Pattern.compile("\\[\\s*'([^']+)'\\s*\\]");

    private static final String[] DIRS = {
        "test-meter-analyzer-config",
        "test-otel-rules",
        "test-envoy-metrics-rules",
        "test-log-mal-rules",
        "test-telegraf-rules",
        "test-zabbix-rules"
    };

    private final MALClassGenerator generator = new MALClassGenerator();

    public static void main(final String[] args) throws Exception {
        final MalInputDataGenerator gen = new MalInputDataGenerator();
        final Path scriptsDir = gen.findScriptsDir();
        if (scriptsDir == null) {
            log.warn("Cannot find scripts/mal directory");
            return;
        }
        int generated = 0;
        int skipped = 0;
        for (final String dir : DIRS) {
            final Path dirPath = scriptsDir.resolve(dir);
            if (Files.isDirectory(dirPath)) {
                final int[] counts = gen.processDirectory(dirPath);
                generated += counts[0];
                skipped += counts[1];
            }
        }
        log.info("Generated: {}, Skipped (already exists): {}", generated, skipped);
    }

    /**
     * Process a directory (recursively) and generate .data.yaml for each MAL YAML.
     *
     * @return int[2]: [generated, skipped]
     */
    int[] processDirectory(final Path dir) throws Exception {
        int generated = 0;
        int skipped = 0;
        final File[] files = dir.toFile().listFiles();
        if (files == null) {
            return new int[]{0, 0};
        }
        for (final File file : files) {
            if (file.isDirectory()) {
                final int[] sub = processDirectory(file.toPath());
                generated += sub[0];
                skipped += sub[1];
                continue;
            }
            if (!file.getName().endsWith(".yaml") && !file.getName().endsWith(".yml")) {
                continue;
            }
            if (file.getName().endsWith(".data.yaml") || file.getName().endsWith(".data.yml")) {
                continue;
            }
            final String baseName = file.getName().replaceAll("\\.(yaml|yml)$", "");
            final File inputFile = new File(file.getParentFile(), baseName + ".data.yaml");
            if (inputFile.exists()) {
                skipped++;
                continue;
            }
            try {
                final String content = generateInputYaml(file);
                if (content != null) {
                    Files.writeString(inputFile.toPath(), content);
                    log.info("  Generated: {}", inputFile.getPath());
                    generated++;
                }
            } catch (Exception e) {
                log.warn("  Error processing {}: {}", file.getName(), e.getMessage());
            }
        }
        return new int[]{generated, skipped};
    }

    @SuppressWarnings("unchecked")
    String generateInputYaml(final File yamlFile) throws IOException {
        final Yaml yaml = new Yaml();
        final String content = Files.readString(yamlFile.toPath());
        final Map<String, Object> config = yaml.load(content);
        if (config == null
            || (!config.containsKey("metricsRules") && !config.containsKey("metrics"))) {
            return null;
        }

        final Object rawPrefix = config.get("expPrefix");
        final String expPrefix = rawPrefix instanceof String ? (String) rawPrefix : "";
        final Object rawSuffix = config.get("expSuffix");
        final String expSuffix = rawSuffix instanceof String ? (String) rawSuffix : "";

        // Support both "metricsRules" (standard) and "metrics" (zabbix)
        List<Map<String, String>> rules =
            (List<Map<String, String>>) config.get("metricsRules");
        if (rules == null) {
            rules = (List<Map<String, String>>) config.get("metrics");
        }
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        // Collect all sample names and labels across all rules in this file
        final Map<String, Set<String>> sampleLabels = new LinkedHashMap<>();
        // sampleName -> label -> {all distinct tagEqual values across all rules}
        final Map<String, Map<String, Set<String>>> perSampleTagEqual = new LinkedHashMap<>();
        // sampleName -> label -> [all tagMatch patterns across all rules]
        final Map<String, Map<String, List<String>>> perSampleTagMatch = new LinkedHashMap<>();
        // Global tagEqual for expSuffix (applies to all samples)
        final Map<String, Set<String>> globalTagEqualValues = new LinkedHashMap<>();
        // Global tagMatch from expSuffix
        final Map<String, List<String>> globalTagMatch = new LinkedHashMap<>();
        final Set<String> closureAccessedLabels = new LinkedHashSet<>();
        final List<String> metricNames = new ArrayList<>();
        boolean anyHistogram = false;
        double valueForEqual = 100.0;

        // Extract constraints from expSuffix (applies to all samples)
        if (!expSuffix.isEmpty()) {
            extractTagEqualAllValues(expSuffix, globalTagEqualValues);
            extractTagMatchAllPatterns(expSuffix, globalTagMatch);
            extractClosureAccessedLabels(expSuffix, closureAccessedLabels);
            extractEntityFunctionLabels(expSuffix, closureAccessedLabels);
        }

        for (final Map<String, String> rule : rules) {
            final String name = rule.get("name");
            final String exp = rule.get("exp");
            if (name == null || exp == null) {
                continue;
            }
            metricNames.add(name);

            String fullExp = exp;
            if (!expPrefix.isEmpty()) {
                fullExp = expPrefix + "." + fullExp;
            }

            // Compile to get metadata (sample names, labels)
            Set<String> ruleSamples = new LinkedHashSet<>();
            try {
                final MalExpression compiled = generator.compile(name, fullExp);
                final ExpressionMetadata meta = compiled.metadata();

                for (final String sample : meta.getSamples()) {
                    ruleSamples.add(sample);
                    final Set<String> labels = sampleLabels.computeIfAbsent(
                        sample, k -> new LinkedHashSet<>());
                    labels.addAll(meta.getAggregationLabels());
                    labels.addAll(meta.getScopeLabels());
                }
                if (meta.isHistogram()) {
                    anyHistogram = true;
                }
            } catch (Exception e) {
                // Compilation failed — extract sample names from expression text
                extractSampleNamesFromText(fullExp, sampleLabels);
            }

            // Extract per-rule tagEqual constraints and associate with this rule's samples
            final Map<String, Set<String>> ruleTagEqual = new LinkedHashMap<>();
            extractTagEqualAllValues(exp, ruleTagEqual);
            for (final String sample : ruleSamples) {
                final Map<String, Set<String>> sampleTe =
                    perSampleTagEqual.computeIfAbsent(sample, k -> new LinkedHashMap<>());
                for (final Map.Entry<String, Set<String>> te : ruleTagEqual.entrySet()) {
                    sampleTe.computeIfAbsent(te.getKey(), k -> new LinkedHashSet<>())
                        .addAll(te.getValue());
                }
            }

            // Extract per-rule tagMatch — infer a matching value for each label and
            // treat it as a multi-value entry (like tagEqual) so that each rule gets
            // a sample variant with the right tagMatch value.
            final Map<String, List<String>> ruleTagMatch = new LinkedHashMap<>();
            extractTagMatchAllPatterns(exp, ruleTagMatch);
            for (final Map.Entry<String, List<String>> tm : ruleTagMatch.entrySet()) {
                final String matchLabel = tm.getKey();
                final String inferredValue = generateMatchingValue(
                    matchLabel, tm.getValue(), name);
                for (final String sample : ruleSamples) {
                    // Add as multi-value (like tagEqual)
                    perSampleTagEqual.computeIfAbsent(sample, k -> new LinkedHashMap<>())
                        .computeIfAbsent(matchLabel, k -> new LinkedHashSet<>())
                        .add(inferredValue);
                    // Also keep patterns for inferLabelValue fallback
                    perSampleTagMatch.computeIfAbsent(sample, k -> new LinkedHashMap<>())
                        .computeIfAbsent(matchLabel, k -> new ArrayList<>())
                        .addAll(tm.getValue());
                }
            }

            // Extract tagNotEqual (non-null) and tagNotMatch labels
            extractClosureAccessedLabels(exp, closureAccessedLabels);
            extractTagNotEqualNullLabels(exp, closureAccessedLabels);
            extractTagNotEqualLabels(exp, ruleSamples, sampleLabels);
            extractTagNotMatchLabels(exp, ruleSamples, sampleLabels);

            // Check for valueEqual
            final Matcher veMatch = VALUE_EQUAL_PATTERN.matcher(exp);
            if (veMatch.find()) {
                valueForEqual = Double.parseDouble(veMatch.group(1));
            }
        }

        if (sampleLabels.isEmpty()) {
            return null;
        }

        // Merge all constraint labels into each sample
        for (final Map.Entry<String, Set<String>> entry : sampleLabels.entrySet()) {
            final String sampleName = entry.getKey();
            final Set<String> labels = entry.getValue();
            labels.addAll(closureAccessedLabels);
            // Add global tagEqual labels (from expSuffix)
            labels.addAll(globalTagEqualValues.keySet());
            // Add global tagMatch labels (from expSuffix)
            labels.addAll(globalTagMatch.keySet());
            // Add per-sample tagEqual labels
            final Map<String, Set<String>> sampleTe = perSampleTagEqual.get(sampleName);
            if (sampleTe != null) {
                labels.addAll(sampleTe.keySet());
            }
            // Add per-sample tagMatch labels
            final Map<String, List<String>> sampleTm = perSampleTagMatch.get(sampleName);
            if (sampleTm != null) {
                labels.addAll(sampleTm.keySet());
            }
        }

        // Build the YAML content
        final StringBuilder sb = new StringBuilder();
        sb.append(LICENSE_HEADER);
        sb.append("input:\n");

        for (final Map.Entry<String, Set<String>> entry : sampleLabels.entrySet()) {
            final String sampleName = entry.getKey();
            final Set<String> labels = entry.getValue();

            sb.append("  ").append(yamlKey(sampleName)).append(":\n");

            // Build effective constraints for THIS sample
            final Map<String, Set<String>> sampleTe = perSampleTagEqual.get(sampleName);
            final Map<String, Set<String>> effectiveTagEqual = new LinkedHashMap<>(globalTagEqualValues);
            if (sampleTe != null) {
                for (final Map.Entry<String, Set<String>> te : sampleTe.entrySet()) {
                    effectiveTagEqual.computeIfAbsent(te.getKey(), k -> new LinkedHashSet<>())
                        .addAll(te.getValue());
                }
            }
            final Map<String, List<String>> sampleTm = perSampleTagMatch.get(sampleName);
            final Map<String, List<String>> effectiveTagMatch = new LinkedHashMap<>(globalTagMatch);
            if (sampleTm != null) {
                for (final Map.Entry<String, List<String>> tm : sampleTm.entrySet()) {
                    effectiveTagMatch.computeIfAbsent(tm.getKey(), k -> new ArrayList<>())
                        .addAll(tm.getValue());
                }
            }
            final Map<String, Set<String>> multiValueLabels = new LinkedHashMap<>();
            for (final String label : labels) {
                final Set<String> vals = effectiveTagEqual.get(label);
                if (vals != null && vals.size() > 1) {
                    multiValueLabels.put(label, vals);
                }
            }

            if (anyHistogram && labels.contains("le")) {
                // Generate multiple samples with cumulative le bucket values
                double cumulativeValue = 0;
                for (final String le : HISTOGRAM_LE_VALUES) {
                    cumulativeValue += 10.0;
                    sb.append("    - labels:\n");
                    for (final String label : labels) {
                        if ("le".equals(label)) {
                            sb.append("        le: '").append(le).append("'\n");
                        } else {
                            final String value = inferLabelValue(
                                label, sampleName, effectiveTagEqual,
                                effectiveTagMatch);
                            sb.append("        ").append(yamlKey(label))
                                .append(": ").append(yamlValue(value)).append("\n");
                        }
                    }
                    sb.append("      value: ").append(cumulativeValue).append("\n");
                }
            } else if (!multiValueLabels.isEmpty()) {
                // Generate one sample per combination of multi-value tagEqual labels
                final List<Map<String, String>> variants =
                    buildLabelVariants(labels, multiValueLabels,
                        sampleName, effectiveTagEqual, effectiveTagMatch);
                for (final Map<String, String> variant : variants) {
                    sb.append("    - labels:\n");
                    for (final Map.Entry<String, String> le : variant.entrySet()) {
                        sb.append("        ").append(yamlKey(le.getKey()))
                            .append(": ").append(yamlValue(le.getValue())).append("\n");
                    }
                    sb.append("      value: ").append(valueForEqual).append("\n");
                }
            } else {
                sb.append("    - labels:\n");
                for (final String label : labels) {
                    final String value = inferLabelValue(
                        label, sampleName, effectiveTagEqual, effectiveTagMatch);
                    sb.append("        ").append(yamlKey(label))
                        .append(": ").append(yamlValue(value)).append("\n");
                }
                sb.append("      value: ").append(valueForEqual).append("\n");
            }
        }

        sb.append("expected:\n");
        for (final String metricName : metricNames) {
            sb.append("  ").append(yamlKey(metricName)).append(":\n");
            sb.append("    min_samples: 1\n");
        }

        return sb.toString();
    }

    private void extractSampleNamesFromText(final String expression,
                                            final Map<String, Set<String>> sampleLabels) {
        // Heuristic: identifiers at start, after binary ops, or after }).
        // Also match after dot when preceded by a closing paren/brace
        final Pattern p = Pattern.compile(
            "(?:^|[+\\-*/()]\\s*|\\}\\)\\s*\\.\\s*)([a-zA-Z_][a-zA-Z0-9_]*)");
        final Matcher m = p.matcher(expression);
        while (m.find()) {
            final String name = m.group(1);
            if (!isKeyword(name) && name.length() > 3) {
                sampleLabels.computeIfAbsent(name, k -> new LinkedHashSet<>());
            }
        }
    }

    private void extractTagEqualAllValues(final String expression,
                                          final Map<String, Set<String>> allValues) {
        final Matcher m = TAG_EQUAL_PATTERN.matcher(expression);
        while (m.find()) {
            allValues.computeIfAbsent(m.group(1), k -> new LinkedHashSet<>())
                .add(m.group(2));
        }
    }

    /**
     * Build label variant maps for samples that need multiple tagEqual values.
     * Produces one map per distinct value of each multi-value label.
     */
    private List<Map<String, String>> buildLabelVariants(
            final Set<String> allLabels,
            final Map<String, Set<String>> multiValueLabels,
            final String sampleName,
            final Map<String, Set<String>> tagEqualAllValues,
            final Map<String, List<String>> tagMatchPatterns) {
        // Start with a single base variant containing all non-multi-value labels
        List<Map<String, String>> variants = new ArrayList<>();
        final Map<String, String> base = new LinkedHashMap<>();
        for (final String label : allLabels) {
            if (!multiValueLabels.containsKey(label)) {
                base.put(label, inferLabelValue(
                    label, sampleName, tagEqualAllValues, tagMatchPatterns));
            }
        }
        variants.add(base);

        // For each multi-value label, expand: each existing variant × each value
        for (final Map.Entry<String, Set<String>> mvEntry : multiValueLabels.entrySet()) {
            final String label = mvEntry.getKey();
            final Set<String> values = mvEntry.getValue();
            final List<Map<String, String>> expanded = new ArrayList<>();
            for (final Map<String, String> existing : variants) {
                for (final String val : values) {
                    final Map<String, String> copy = new LinkedHashMap<>(existing);
                    copy.put(label, val);
                    expanded.add(copy);
                }
            }
            variants = expanded;
        }
        return variants;
    }

    private void extractTagMatchAllPatterns(final String expression,
                                            final Map<String, List<String>> patterns) {
        final Matcher m = TAG_MATCH_PATTERN.matcher(expression);
        while (m.find()) {
            patterns.computeIfAbsent(m.group(1), k -> new ArrayList<>())
                .add(m.group(2));
        }
    }

    private void extractClosureAccessedLabels(final String expression,
                                              final Set<String> labels) {
        final Matcher m1 = CLOSURE_TAG_ACCESS_PATTERN.matcher(expression);
        while (m1.find()) {
            final String label = m1.group(1);
            if (!"put".equals(label) && !"get".equals(label) && !"trim".equals(label)
                && !"toString".equals(label) && !"size".equals(label)
                && !"length".equals(label)) {
                labels.add(label);
            }
        }
        final Matcher m2 = CLOSURE_TAG_BRACKET_PATTERN.matcher(expression);
        while (m2.find()) {
            labels.add(m2.group(1));
        }
    }

    /**
     * Extract label names from entity function arguments in expSuffix.
     * Entity functions like {@code instance(['host_name'], ['service_instance_id'], Layer.MYSQL)}
     * use {@code ['label']} arguments to identify which input labels map to entity fields.
     * These labels must be present in all input samples.
     */
    private void extractEntityFunctionLabels(final String expression,
                                             final Set<String> labels) {
        final Matcher funcMatcher = ENTITY_FUNC_PATTERN.matcher(expression);
        while (funcMatcher.find()) {
            // Find the matching closing paren for this entity function call
            final int argsStart = funcMatcher.end();
            int depth = 1;
            int argsEnd = argsStart;
            for (int i = argsStart; i < expression.length() && depth > 0; i++) {
                final char c = expression.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
                argsEnd = i;
            }
            final String argsStr = expression.substring(argsStart, argsEnd);
            // Extract all ['label'] arguments within the function call
            final Matcher labelMatcher = STRING_LIST_ARG_PATTERN.matcher(argsStr);
            while (labelMatcher.find()) {
                labels.add(labelMatcher.group(1));
            }
        }
    }

    private void extractTagNotEqualNullLabels(final String expression,
                                              final Set<String> labels) {
        final Matcher m = TAG_NOT_EQUAL_NULL_PATTERN.matcher(expression);
        while (m.find()) {
            labels.add(m.group(1));
        }
    }

    private void extractTagNotEqualLabels(final String expression,
                                          final Set<String> ruleSamples,
                                          final Map<String, Set<String>> sampleLabels) {
        final Matcher m = TAG_NOT_EQUAL_PATTERN.matcher(expression);
        while (m.find()) {
            final String label = m.group(1);
            for (final String sample : ruleSamples) {
                sampleLabels.computeIfAbsent(sample, k -> new LinkedHashSet<>()).add(label);
            }
        }
    }

    private void extractTagNotMatchLabels(final String expression,
                                          final Set<String> ruleSamples,
                                          final Map<String, Set<String>> sampleLabels) {
        final Matcher m = TAG_NOT_MATCH_PATTERN.matcher(expression);
        while (m.find()) {
            final String label = m.group(1);
            for (final String sample : ruleSamples) {
                sampleLabels.computeIfAbsent(sample, k -> new LinkedHashSet<>()).add(label);
            }
        }
    }

    String inferLabelValue(final String label,
                           final String sampleName,
                           final Map<String, Set<String>> tagEqualAllValues,
                           final Map<String, List<String>> tagMatchPatterns) {
        // Check tagEqual constraints — use first value (for single-value labels)
        final Set<String> eqVals = tagEqualAllValues.get(label);
        if (eqVals != null && !eqVals.isEmpty()) {
            return eqVals.iterator().next();
        }

        // Check tagMatch constraints — generate a value matching ALL patterns
        final List<String> patterns = tagMatchPatterns.get(label);
        if (patterns != null && !patterns.isEmpty()) {
            return generateMatchingValue(label, patterns, sampleName);
        }

        // Known label patterns
        switch (label) {
            case "service":
            case "service_name":
                return "test-service";
            case "service_namespace":
                return "test-ns";
            case "cluster_name":
            case "cluster":
                return "test-cluster";
            case "instance":
            case "service_instance_id":
                return "test-instance";
            case "endpoint":
                return "/test";
            case "host_name":
            case "node_identifier_host_name":
                return "test-host";
            case "node":
            case "node_id":
                return "test-node";
            case "app":
                return "test-app";
            case "job_name":
                return "test-monitoring";
            case "topic":
                return "test-topic";
            case "queue":
                return "test-queue";
            case "broker":
            case "brokerName":
                return "test-broker";
            case "pod":
            case "pod_name":
                return "test-pod";
            case "namespace":
                return "test-namespace";
            case "container":
                return "test-container";
            case "mode":
                return "user";
            case "mountpoint":
                return "/";
            case "device":
                return "eth0";
            case "fstype":
                return "ext4";
            case "area":
                return "heap";
            case "pool":
                return "PS_Eden_Space";
            case "gc":
                return "PS Scavenge";
            case "le":
                return "100";
            case "type":
                return "cds";
            case "status":
            case "state":
                return "active";
            case "code":
                return "200";
            case "name":
                return "test-name";
            case "level":
                return "ERROR";
            case "pipe":
                return "test-pipe";
            case "pipeline":
                return "test-pipeline";
            case "direction":
                return "in";
            case "route":
                return "test-route";
            case "protocol":
                return "http";
            case "is_ssl":
                return "false";
            case "component":
                return "49";
            case "created_by":
                return "test-creator";
            case "source":
                return "test-source";
            case "plugin_name":
                return "test-plugin";
            case "inter_type":
                return "test-type";
            case "metric_type":
            case "metricName":
                return "test-metric";
            case "kind":
                return "test-kind";
            case "operation":
                return "test-op";
            case "catalog":
                return "test-catalog";
            case "listener":
                return "test-listener";
            case "event":
                return "test-event";
            case "dimensionality":
                return "minute";
            case "shared_dict":
                return "test-dict";
            case "key":
                return "test-key";
            case "color":
                return "green";
            case "process_name":
                return "test-process";
            case "layer":
                return "GENERAL";
            case "uri":
                return "/test-uri";
            case "pid":
                return "12345";
            case "side":
                return "client";
            case "client_local":
                return "false";
            case "server_local":
                return "true";
            case "client_address":
                return "10.0.0.1:8080";
            case "server_address":
                return "10.0.0.2:9090";
            case "client_process_id":
            case "server_process_id":
                return "test-process-id";
            case "cloud_account_id":
                return "123456789";
            case "cloud_region":
                return "us-east-1";
            case "cloud_provider":
                return "aws";
            case "Namespace":
                return "AWS/DynamoDB";
            case "Operation":
                return "GetItem";
            case "TableName":
                return "test-table";
            case "destinationName":
                return "test-destination";
            case "destinationType":
                return "Queue";
            case "tag":
                return "1.19.0";
            case "skywalking_service":
                return "test-sw-service";
            case "oscal_control_bundle":
            case "control_bundle":
                return "nist-800-53";
            case "oscal_control_name":
            case "control_name":
                return "AC-1";
            case "secret_name":
                return "test-secret";
            case "partition":
                return "0";
            case "metrics_name":
                return "test-metrics";
            case "cmd":
                return "get";
            default:
                return "test-value";
        }
    }

    /**
     * Generate a value that matches ALL given tagMatch regex patterns for a label.
     */
    private String generateMatchingValue(final String label, final List<String> patterns,
                                         final String sampleName) {
        // Handle common multi-pattern label cases
        if ("metrics_name".equals(label)) {
            return generateMetricsNameValue(patterns);
        }

        // For single pattern, use simple matching
        final String pattern = patterns.get(0);
        if ("gc".equals(label)) {
            if (pattern.contains("PS Scavenge")) {
                return "PS Scavenge";
            }
            if (pattern.contains("PS MarkSweep")) {
                return "PS MarkSweep";
            }
            return pattern.split("\\|")[0];
        }
        if ("Operation".equals(label)) {
            return pattern.split("\\|")[0];
        }
        // Generic: take first alternative or strip regex syntax
        final String stripped = pattern
            .replace(".*", "test")
            .replace(".+", "test")
            .replaceAll("\\[\\^.\\]\\+", "test")
            .replace("(", "").replace(")", "")
            .replace("^", "").replace("$", "");
        if (stripped.contains("|")) {
            return stripped.split("\\|")[0];
        }
        return stripped;
    }

    private String generateMetricsNameValue(final List<String> patterns) {
        // Envoy metrics_name patterns combine prefix matching with suffix matching
        // e.g., [".+membership_healthy", "cluster.outbound.+|cluster.inbound.+"]
        // Need a value matching ALL patterns simultaneously

        // Collect suffix/content requirements and prefix requirements
        String prefix = "cluster.outbound.test-cluster";
        String suffix = "";

        for (final String p : patterns) {
            if (p.contains("ssl") && p.contains("expiration")) {
                return "cluster.outbound.test-cluster.ssl.certificate.test-cert.expiration_unix_time_seconds";
            }
            if (p.contains("membership_healthy")) {
                suffix = ".membership_healthy";
            } else if (p.contains("membership_total")) {
                suffix = ".membership_total";
            } else if (p.contains("cx_active")) {
                suffix = ".upstream_cx_active";
            } else if (p.contains("cx_connect_fail")) {
                suffix = ".upstream_cx_connect_fail";
            } else if (p.contains("rq_active")) {
                suffix = ".upstream_rq_active";
            } else if (p.contains("rq_pending_active")) {
                suffix = ".upstream_rq_pending_active";
            } else if (p.contains("lb_healthy_panic")) {
                suffix = ".lb_healthy_panic";
            } else if (p.contains("cx_none_healthy")) {
                suffix = ".upstream_cx_none_healthy";
            } else if (p.contains("cluster.outbound") || p.contains("cluster.inbound")) {
                // Prefix pattern — already handled
                prefix = "cluster.outbound.test-cluster";
            }
        }

        if (!suffix.isEmpty()) {
            return prefix + suffix;
        }

        // Fallback: try to satisfy all patterns
        for (final String p : patterns) {
            final String stripped = p
                .replace(".*", "test")
                .replace(".+", "test")
                .replace("(", "").replace(")", "")
                .replace("^", "").replace("$", "");
            if (stripped.contains("|")) {
                return stripped.split("\\|")[0];
            }
            return stripped;
        }
        return "test-metric";
    }

    private static boolean isKeyword(final String name) {
        switch (name) {
            case "def":
            case "if":
            case "else":
            case "return":
            case "null":
            case "true":
            case "false":
            case "in":
            case "String":
            case "tag":
            case "sum":
            case "avg":
            case "min":
            case "max":
            case "count":
            case "rate":
            case "increase":
            case "irate":
            case "histogram":
            case "service":
            case "instance":
            case "endpoint":
            case "downsampling":
            case "forEach":
            case "tagEqual":
            case "tagNotEqual":
            case "tagMatch":
            case "tagNotMatch":
            case "valueEqual":
            case "multiply":
            case "filter":
            case "time":
            case "Layer":
            case "SUM":
            case "AVG":
            case "MIN":
            case "MAX":
            case "LATEST":
            case "MEAN":
            case "ProcessRegistry":
            // Closure parameter names (not sample names)
            case "tags":
            case "me":
            case "prefix":
            case "key":
            case "result":
            case "protocol":
            case "ssl":
            case "matcher":
            case "parts":
            case "java":
                return true;
            default:
                return false;
        }
    }

    private static String yamlKey(final String key) {
        // Quote keys that contain special chars
        if (key.contains("-") || key.contains(".") || key.contains(" ")) {
            return "'" + key + "'";
        }
        return key;
    }

    private static String yamlValue(final String value) {
        if (value == null) {
            return "''";
        }
        // Quote values that could be misinterpreted
        if (value.contains(":") || value.contains("#") || value.contains("{")
            || value.contains("}") || value.contains("[") || value.contains("]")
            || value.contains("'") || value.contains("\"") || value.contains(",")
            || value.contains("&") || value.contains("*") || value.contains("!")
            || value.contains("|") || value.contains(">") || value.contains("%")
            || value.contains("@") || value.contains("`")
            || "true".equals(value) || "false".equals(value)
            || "null".equals(value) || "yes".equals(value) || "no".equals(value)) {
            return "'" + value.replace("'", "''") + "'";
        }
        return value;
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
}
