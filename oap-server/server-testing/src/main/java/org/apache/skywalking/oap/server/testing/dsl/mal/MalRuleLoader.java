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

package org.apache.skywalking.oap.server.testing.dsl.mal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.testing.dsl.DslRuleLoader;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads MAL rules from YAML files in a scripts directory structure.
 * Handles both {@code metricsRules} (standard) and {@code metrics} (Zabbix) keys,
 * applies {@code expPrefix}/{@code expSuffix}/{@code metricPrefix} transformations,
 * and disambiguates duplicate rule names.
 */
public final class MalRuleLoader {

    private MalRuleLoader() {
    }

    /**
     * Loads all MAL rules from the specified directories under {@code scriptsDir}.
     *
     * @param scriptsDir  the {@code scripts/mal/} root directory
     * @param directories subdirectory names to scan (e.g.,
     *                    {@code "test-otel-rules"}, {@code "test-meter-analyzer-config"})
     * @return rules grouped by YAML file path (e.g., {@code "test-otel-rules/vm.yaml"})
     */
    /**
     * Loads all MAL rules from the specified directories under {@code scriptsDir}.
     *
     * @param scriptsDir  the {@code scripts/mal/} root directory
     * @param directories subdirectory names to scan (e.g.,
     *                    {@code "test-otel-rules"}, {@code "test-meter-analyzer-config"})
     * @return rules grouped by YAML file path (e.g., {@code "test-otel-rules/vm.yaml"})
     */
    public static Map<String, List<MalTestRule>> loadAllRules(
            final Path scriptsDir,
            final String[] directories) throws Exception {
        final Map<String, List<MalTestRule>> result = new LinkedHashMap<>();
        final Yaml yaml = new Yaml();

        for (final String dirName : directories) {
            final Path dir = scriptsDir.resolve(dirName);
            if (!java.nio.file.Files.isDirectory(dir)) {
                continue;
            }
            final List<File> yamlFiles = new ArrayList<>();
            DslRuleLoader.collectYamlFiles(dir.toFile(), yamlFiles);
            for (final File file : yamlFiles) {
                collectRulesFromFile(yaml, file, dirName, result);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void collectRulesFromFile(
            final Yaml yaml, final File file,
            final String dirName,
            final Map<String, List<MalTestRule>> result) throws Exception {

        final String content = Files.readString(file.toPath());
        final Map<String, Object> config = yaml.load(content);
        if (config == null
                || (!config.containsKey("metricsRules")
                    && !config.containsKey("metrics"))) {
            return;
        }

        final Object rawSuffix = config.get("expSuffix");
        final String expSuffix = rawSuffix instanceof String
            ? (String) rawSuffix : "";
        final Object rawPrefix = config.get("expPrefix");
        final String expPrefix = rawPrefix instanceof String
            ? (String) rawPrefix : "";
        final Object rawMetricPrefix = config.get("metricPrefix");
        final String metricPrefix = rawMetricPrefix instanceof String
            ? (String) rawMetricPrefix : null;
        final Object rawFilter = config.get("filter");
        final String filter = rawFilter instanceof String
            ? ((String) rawFilter).trim() : null;

        // Support both "metricsRules" (standard) and "metrics" (zabbix)
        List<Map<String, String>> rules =
            (List<Map<String, String>>) config.get("metricsRules");
        if (rules == null) {
            rules = (List<Map<String, String>>) config.get("metrics");
        }
        if (rules == null) {
            return;
        }

        // Load companion .data.yaml
        Map<String, Object> inputConfig =
            DslRuleLoader.loadCompanionFile(file, ".data.yaml");

        final String yamlName = dirName + "/" + file.getName();
        final List<MalTestRule> malRules = new ArrayList<>();
        final Map<String, Integer> nameCount = new HashMap<>();
        final String[] lines = content.split("\n");

        for (final Map<String, String> rule : rules) {
            final String name = rule.get("name");
            final String exp = rule.get("exp");
            if (name == null || exp == null) {
                continue;
            }
            final int count = nameCount.merge(name, 1, Integer::sum);
            final String uniqueName = count > 1 ? name + "_" + count : name;
            final String fullExp = formatExp(expPrefix, expSuffix, exp);
            final int lineNo = DslRuleLoader.findRuleLine(lines, name, count);
            malRules.add(new MalTestRule(
                uniqueName, fullExp, filter, metricPrefix,
                inputConfig, dirName, file, lineNo));
        }

        if (!malRules.isEmpty()) {
            result.put(yamlName, malRules);
        }
    }

    /**
     * Data-driven loading: scans {@code dataDir} for {@code .data.yaml} files,
     * resolves the script from the optional {@code script:} field in each data file.
     * If {@code script:} is absent, looks for a co-located {@code .yaml} file with
     * the same base name.
     *
     * <p>The {@code script:} path is resolved by probing common repository root
     * locations relative to the current working directory.
     *
     * @param dataDir  root directory to scan for {@code .data.yaml} files
     * @return rules grouped by data file path relative to {@code dataDir}
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<MalTestRule>> loadFromDataFiles(
            final Path dataDir) throws Exception {
        final Map<String, List<MalTestRule>> result = new LinkedHashMap<>();
        final Yaml yaml = new Yaml();

        final List<File> dataFiles = new ArrayList<>();
        collectDataFiles(dataDir.toFile(), dataFiles);

        for (final File dataFile : dataFiles) {
            final String dataContent = Files.readString(dataFile.toPath());
            final Map<String, Object> dataConfig = yaml.load(dataContent);
            if (dataConfig == null) {
                continue;
            }

            // Resolve the script file
            final File scriptFile;
            final Object scriptPath = dataConfig.get("script");
            if (scriptPath instanceof String) {
                scriptFile = resolveScript((String) scriptPath, dataFile);
                if (scriptFile == null) {
                    throw new IllegalStateException(
                        "Script not found: " + scriptPath
                            + " (referenced by " + dataFile.getName() + ")");
                }
            } else {
                // Co-located: same base name with .yaml extension
                final String baseName = dataFile.getName()
                    .replaceFirst("\\.data\\.yaml$", "");
                scriptFile = new File(dataFile.getParent(), baseName + ".yaml");
                if (!scriptFile.exists()) {
                    continue;
                }
            }

            // Parse the script file
            final String scriptContent = Files.readString(scriptFile.toPath());
            final Map<String, Object> config = yaml.load(scriptContent);
            if (config == null
                    || (!config.containsKey("metricsRules")
                        && !config.containsKey("metrics"))) {
                continue;
            }

            final Object rawSuffix = config.get("expSuffix");
            final String expSuffix = rawSuffix instanceof String
                ? (String) rawSuffix : "";
            final Object rawPrefix = config.get("expPrefix");
            final String expPrefix = rawPrefix instanceof String
                ? (String) rawPrefix : "";
            final Object rawMetricPrefix = config.get("metricPrefix");
            final String metricPrefix = rawMetricPrefix instanceof String
                ? (String) rawMetricPrefix : null;
            final Object rawFilter = config.get("filter");
            final String filter = rawFilter instanceof String
                ? ((String) rawFilter).trim() : null;

            List<Map<String, String>> rules =
                (List<Map<String, String>>) config.get("metricsRules");
            if (rules == null) {
                rules = (List<Map<String, String>>) config.get("metrics");
            }
            if (rules == null) {
                continue;
            }

            // Use relative path from dataDir as the test group name
            final String relPath = dataDir.toFile().toURI()
                .relativize(dataFile.toURI()).getPath();
            final String dirName = relPath.contains("/")
                ? relPath.substring(0, relPath.indexOf('/')) : "";

            final List<MalTestRule> malRules = new ArrayList<>();
            final Map<String, Integer> nameCount = new HashMap<>();
            final String[] lines = scriptContent.split("\n");

            for (final Map<String, String> rule : rules) {
                final String name = rule.get("name");
                final String exp = rule.get("exp");
                if (name == null || exp == null) {
                    continue;
                }
                final int count = nameCount.merge(name, 1, Integer::sum);
                final String uniqueName = count > 1 ? name + "_" + count : name;
                final String fullExp = formatExp(expPrefix, expSuffix, exp);
                final int lineNo = DslRuleLoader.findRuleLine(lines, name, count);
                malRules.add(new MalTestRule(
                    uniqueName, fullExp, filter, metricPrefix,
                    dataConfig, dirName, scriptFile, lineNo));
            }

            if (!malRules.isEmpty()) {
                result.put(relPath.replaceFirst("\\.data\\.yaml$", ".yaml"), malRules);
            }
        }
        return result;
    }

    /**
     * Resolves a script path by probing common repository root locations.
     */
    private static File resolveScript(final String scriptPath, final File dataFile) {
        // Try direct path first (when CWD is repo root)
        File f = Path.of(scriptPath).toFile();
        if (f.exists()) {
            return f;
        }
        // Probe relative to CWD: ../../../ covers oap-server/analyzer/*/
        for (int i = 1; i <= 5; i++) {
            final String prefix = "../".repeat(i);
            f = Path.of(prefix + scriptPath).toFile();
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    /**
     * Recursively collects {@code .data.yaml} files from a directory.
     */
    private static void collectDataFiles(final File dir, final List<File> result) {
        final File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (final File child : children) {
            if (child.isDirectory()) {
                collectDataFiles(child, result);
            } else if (child.getName().endsWith(".data.yaml")) {
                result.add(child);
            }
        }
    }

    /**
     * Replicates production {@code MetricConvert.formatExp()} behavior:
     * inserts {@code expPrefix} after the metric name (first dot-segment),
     * and appends {@code expSuffix} after the whole expression.
     */
    public static String formatExp(final String expPrefix,
                                   final String expSuffix,
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
}
