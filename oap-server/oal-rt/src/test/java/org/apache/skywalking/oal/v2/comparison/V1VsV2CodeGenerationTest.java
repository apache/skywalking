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

package org.apache.skywalking.oal.v2.comparison;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oal.rt.parser.AnalysisResult;
import org.apache.skywalking.oal.rt.parser.OALScripts;
import org.apache.skywalking.oal.rt.parser.ScriptParser;
import org.apache.skywalking.oal.rt.util.OALClassGenerator;
import org.apache.skywalking.oal.v2.generator.CodeGenModel;
import org.apache.skywalking.oal.v2.generator.MetricDefinitionEnricher;
import org.apache.skywalking.oal.v2.generator.OALClassGeneratorV2;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.oal.rt.CoreOALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Comprehensive comparison test for V1 vs V2 code generation.
 *
 * This test:
 * 1. Loads real production OAL scripts from server-starter/resources/oal/
 * 2. Generates complete Java source code with both V1 and V2
 * 3. Compares generated code for each metric
 * 4. Reports textual and semantic differences
 *
 * NOTE: This test requires full OAP runtime environment (DefaultScopeDefine initialization).
 * It will be skipped if the environment is not available.
 */
@Slf4j
public class V1VsV2CodeGenerationTest {

    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String METRICS_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.";

    /**
     * Find the OAL scripts directory in the project.
     */
    private File findOALScriptsDir() {
        String[] possiblePaths = {
            "oap-server/server-starter/src/main/resources/oal",
            "../server-starter/src/main/resources/oal",
            "../../server-starter/src/main/resources/oal"
        };

        for (String path : possiblePaths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                log.debug("Found OAL scripts directory at: {}", dir.getAbsolutePath());
                return dir;
            }
        }

        throw new IllegalStateException("Could not find OAL scripts directory. Tried: " +
            String.join(", ", possiblePaths));
    }

    @BeforeAll
    public static void setup() throws Exception {
        // Initialize DefaultScopeDefine by scanning all @ScopeDeclaration annotations
        log.info("Initializing DefaultScopeDefine for V1 vs V2 comparison tests...");
        AnnotationScan scopeScan = new AnnotationScan();
        scopeScan.registerListener(new DefaultScopeDefine.Listener());
        scopeScan.scan();
        log.info("DefaultScopeDefine initialized successfully");

        // Initialize SourceDecoratorManager by scanning decorator classes
        log.info("Initializing SourceDecoratorManager for V1 vs V2 comparison tests...");
        org.apache.skywalking.oap.server.core.source.SourceReceiverImpl sourceReceiver =
            new org.apache.skywalking.oap.server.core.source.SourceReceiverImpl();
        sourceReceiver.scan();
        log.info("SourceDecoratorManager initialized successfully with {} decorators",
            org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager.DECORATOR_MAP.size());
    }

    @AfterAll
    public static void cleanup() {
        // Reset DefaultScopeDefine after tests
        DefaultScopeDefine.reset();
        // Clear decorators
        org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager.DECORATOR_MAP.clear();
        log.info("DefaultScopeDefine reset and decorators cleared");
    }

    /**
     * Compare V1 and V2 generated code for core.oal.
     *
     * This test loads core.oal (the main metrics definitions), generates code with both
     * V1 and V2, and compares the results.
     */
    @Test
    public void testCompareCoreOALCodeGeneration() throws Exception {
        File oalDir = findOALScriptsDir();
        File oalFile = new File(oalDir, "core.oal");

        if (!oalFile.exists()) {
            log.warn("Skipping test - core.oal not found at: {}", oalFile.getAbsolutePath());
            return;
        }

        log.info("========================================");
        log.info("Comparing V1 vs V2 Code Generation: core.oal");
        log.info("========================================");

        ComparisonResult result = compareOALFile(oalFile, "core.oal");

        log.info("\n" + result.getSummary());

        // Assert no critical differences
        assertEquals(0, result.getCriticalDifferences(),
            "V1 and V2 should have no critical differences");
    }

    /**
     * Compare V1 and V2 generated code for all production OAL files.
     *
     * This is the comprehensive test validating V2 generates identical code to V1
     * for all production OAL scripts.
     */
    @Test
    public void testCompareAllOALFilesCodeGeneration() throws Exception {
        File oalDir = findOALScriptsDir();
        String[] oalFiles = {
            "core.oal",
            "java-agent.oal",
            "dotnet-agent.oal",
            "browser.oal",
            "mesh.oal",
            "ebpf.oal",
            "tcp.oal",
            "cilium.oal"
        };

        log.info("========================================");
        log.info("Comparing V1 vs V2 Code Generation: ALL OAL Files");
        log.info("========================================");

        int totalFiles = 0;
        int totalMetrics = 0;
        int totalExactMatches = 0;
        int totalSemanticMatches = 0;
        int totalDifferences = 0;

        for (String fileName : oalFiles) {
            File oalFile = new File(oalDir, fileName);

            if (!oalFile.exists()) {
                log.warn("Skipping {} - file not found", fileName);
                continue;
            }

            ComparisonResult result = compareOALFile(oalFile, fileName);

            totalFiles++;
            totalMetrics += result.getTotalMetrics();
            totalExactMatches += result.getExactMatches();
            totalSemanticMatches += result.getSemanticMatches();
            totalDifferences += result.getCriticalDifferences();

            log.info("\n" + result.getSummary());
        }

        // Overall summary
        log.info("\n========================================");
        log.info("OVERALL SUMMARY");
        log.info("========================================");
        log.info("Files compared: {}", totalFiles);
        log.info("Total metrics: {}", totalMetrics);
        log.info("Exact matches: {} ({} %)", totalExactMatches,
            totalMetrics > 0 ? (totalExactMatches * 100 / totalMetrics) : 0);
        log.info("Semantic matches: {} ({} %)", totalSemanticMatches,
            totalMetrics > 0 ? (totalSemanticMatches * 100 / totalMetrics) : 0);
        log.info("Critical differences: {}", totalDifferences);
        log.info("========================================");

        assertEquals(0, totalDifferences, "V1 and V2 should have no critical differences");
    }

    /**
     * Compare V1 and V2 code generation for a single OAL file.
     */
    private ComparisonResult compareOALFile(File oalFile, String fileName) throws Exception {
        ComparisonResult result = new ComparisonResult(fileName);

        // Parse with V1
        ScriptParser v1Parser = ScriptParser.createFromFile(
            new FileReader(oalFile),
            SOURCE_PACKAGE);
        OALScripts v1Scripts = v1Parser.parse();

        // Parse with V2
        OALScriptParserV2 v2Parser = OALScriptParserV2.parse(new FileReader(oalFile), fileName);

        // Create generators
        OALDefine oalDefine = CoreOALDefine.INSTANCE;
        OALClassGenerator v1Generator = new OALClassGenerator(oalDefine);
        OALClassGeneratorV2 v2Generator = new OALClassGeneratorV2(oalDefine);

        // Create enricher for V2
        MetricDefinitionEnricher v2Enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);

        // Build maps for comparison
        Map<String, AnalysisResult> v1MetricsMap = new HashMap<>();
        v1Scripts.getMetricsStmts().forEach(metric -> {
            v1MetricsMap.put(metric.getVarName(), metric);
        });

        Map<String, MetricDefinition> v2MetricsMap = new HashMap<>();
        v2Parser.getMetrics().forEach(metric -> {
            v2MetricsMap.put(metric.getName(), metric);
        });

        // Compare each metric
        for (MetricDefinition v2Metric : v2Parser.getMetrics()) {
            String metricName = v2Metric.getName();
            AnalysisResult v1Metric = v1MetricsMap.get(metricName);

            if (v1Metric == null) {
                result.addMissingInV1(metricName);
                log.warn("  ❌ {}: Metric missing in V1", metricName);
                continue;
            }

            try {
                // Initialize V1 metric fields required by templates
                v1Metric.setMetricsClassPackage(oalDefine.getDynamicMetricsClassPackage());
                v1Metric.setSourcePackage(oalDefine.getSourcePackage());

                // Generate source code with V1
                String v1Source = v1Generator.generateMetricsClassSourceCode(v1Metric);

                // Enrich and generate source code with V2
                CodeGenModel v2Model = v2Enricher.enrich(v2Metric);
                String v2Source = v2Generator.generateMetricsClassSourceCode(v2Model);

                // For first few metrics, save to files for manual inspection
                if (metricName.equals("service_resp_time") || metricName.equals("service_sla") || metricName.equals("service_cpm")) {
                    try {
                        java.nio.file.Files.writeString(
                            java.nio.file.Path.of("/tmp/v1-" + metricName + ".java"),
                            v1Source
                        );
                        java.nio.file.Files.writeString(
                            java.nio.file.Path.of("/tmp/v2-" + metricName + ".java"),
                            v2Source
                        );
                        log.info("✓ Saved {} comparison files to /tmp/", metricName);
                    } catch (Exception e) {
                        log.warn("Failed to save comparison files for {}: {}", metricName, e.getMessage());
                    }
                }

                // Compare
                MetricComparison comparison = compareMetricSource(metricName, v1Source, v2Source);
                result.addComparison(comparison);

                if (comparison.isExactMatch()) {
                    log.debug("  ✅ {}: Exact match", metricName);
                } else if (comparison.isSemanticMatch()) {
                    log.info("  ⚠️  {}: Semantic match (whitespace/formatting differences)", metricName);
                } else {
                    log.error("  ❌ {}: Critical differences found", metricName);
                    log.error("     Differences: {}", comparison.getDifferences());
                }

            } catch (Exception e) {
                result.addError(metricName, e.getMessage());
                log.error("  ❌ {}: Error during generation: {}", metricName, e.getMessage());
                throw e;
            }
        }

        return result;
    }

    /**
     * Compare source code for a single metric.
     */
    private MetricComparison compareMetricSource(String metricName, String v1Source, String v2Source) {
        MetricComparison comparison = new MetricComparison(metricName);

        // 1. Exact match (including whitespace)
        if (v1Source.equals(v2Source)) {
            comparison.setExactMatch(true);
            comparison.setSemanticMatch(true);
            return comparison;
        }

        // 2. Normalize and compare (semantic match)
        String v1Normalized = normalizeSource(v1Source);
        String v2Normalized = normalizeSource(v2Source);

        if (v1Normalized.equals(v2Normalized)) {
            comparison.setSemanticMatch(true);
            comparison.addDifference("Whitespace/formatting differences only");
            return comparison;
        }

        // 3. Find actual differences
        comparison.setExactMatch(false);
        comparison.setSemanticMatch(false);
        comparison.addDifference(findDifferences(v1Source, v2Source));

        return comparison;
    }

    /**
     * Normalize source code for semantic comparison.
     *
     * Removes whitespace, comments, normalizes formatting.
     */
    private String normalizeSource(String source) {
        return source
            // Remove single-line comments
            .replaceAll("//.*?\n", "\n")
            // Remove multi-line comments
            .replaceAll("/\\*.*?\\*/", "")
            // Normalize whitespace
            .replaceAll("\\s+", " ")
            // Remove spaces around punctuation
            .replaceAll("\\s*([{}();,=<>])\\s*", "$1")
            .trim();
    }

    /**
     * Find and describe differences between two source strings.
     */
    private String findDifferences(String v1Source, String v2Source) {
        StringBuilder diff = new StringBuilder();

        String[] v1Lines = v1Source.split("\n");
        String[] v2Lines = v2Source.split("\n");

        int minLines = Math.min(v1Lines.length, v2Lines.length);
        int diffCount = 0;

        for (int i = 0; i < minLines && diffCount < 5; i++) {
            if (!v1Lines[i].equals(v2Lines[i])) {
                diff.append(String.format("Line %d differs:\nV1: %s\nV2: %s\n",
                    i + 1, v1Lines[i].trim(), v2Lines[i].trim()));
                diffCount++;
            }
        }

        if (v1Lines.length != v2Lines.length) {
            diff.append(String.format("Line count differs: V1=%d, V2=%d\n",
                v1Lines.length, v2Lines.length));
        }

        return diff.toString();
    }

    /**
     * Comparison result for a single metric.
     */
    private static class MetricComparison {
        private final String metricName;
        private boolean exactMatch = false;
        private boolean semanticMatch = false;
        private final StringBuilder differences = new StringBuilder();

        public MetricComparison(String metricName) {
            this.metricName = metricName;
        }

        public void setExactMatch(boolean exactMatch) {
            this.exactMatch = exactMatch;
        }

        public void setSemanticMatch(boolean semanticMatch) {
            this.semanticMatch = semanticMatch;
        }

        public void addDifference(String diff) {
            if (differences.length() > 0) {
                differences.append("; ");
            }
            differences.append(diff);
        }

        public boolean isExactMatch() {
            return exactMatch;
        }

        public boolean isSemanticMatch() {
            return semanticMatch;
        }

        public String getDifferences() {
            return differences.toString();
        }

        public String getMetricName() {
            return metricName;
        }
    }

    /**
     * Comparison result for an entire OAL file.
     */
    private static class ComparisonResult {
        private final String fileName;
        private int totalMetrics = 0;
        private int exactMatches = 0;
        private int semanticMatches = 0;
        private int criticalDifferences = 0;
        private final Map<String, String> missingInV1 = new HashMap<>();
        private final Map<String, String> errors = new HashMap<>();
        private final Map<String, MetricComparison> comparisons = new HashMap<>();

        public ComparisonResult(String fileName) {
            this.fileName = fileName;
        }

        public void addComparison(MetricComparison comparison) {
            totalMetrics++;
            comparisons.put(comparison.getMetricName(), comparison);

            if (comparison.isExactMatch()) {
                exactMatches++;
                semanticMatches++;
            } else if (comparison.isSemanticMatch()) {
                semanticMatches++;
            } else {
                criticalDifferences++;
            }
        }

        public void addMissingInV1(String metricName) {
            totalMetrics++;
            criticalDifferences++;
            missingInV1.put(metricName, "Missing in V1");
        }

        public void addError(String metricName, String error) {
            criticalDifferences++;
            errors.put(metricName, error);
        }

        public int getTotalMetrics() {
            return totalMetrics;
        }

        public int getExactMatches() {
            return exactMatches;
        }

        public int getSemanticMatches() {
            return semanticMatches;
        }

        public int getCriticalDifferences() {
            return criticalDifferences;
        }

        public String getSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append("========================================\n");
            summary.append("File: ").append(fileName).append("\n");
            summary.append("========================================\n");
            summary.append("Total metrics: ").append(totalMetrics).append("\n");
            summary.append("Exact matches: ").append(exactMatches).append(" (")
                .append(totalMetrics > 0 ? (exactMatches * 100 / totalMetrics) : 0).append(" %)\n");
            summary.append("Semantic matches: ").append(semanticMatches).append(" (")
                .append(totalMetrics > 0 ? (semanticMatches * 100 / totalMetrics) : 0).append(" %)\n");
            summary.append("Critical differences: ").append(criticalDifferences).append("\n");

            if (!missingInV1.isEmpty()) {
                summary.append("\nMissing in V1:\n");
                missingInV1.forEach((name, reason) ->
                    summary.append("  - ").append(name).append(": ").append(reason).append("\n"));
            }

            if (!errors.isEmpty()) {
                summary.append("\nErrors:\n");
                errors.forEach((name, error) ->
                    summary.append("  - ").append(name).append(": ").append(error).append("\n"));
            }

            summary.append("========================================");
            return summary.toString();
        }
    }
}
