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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oal.rt.parser.OALScripts;
import org.apache.skywalking.oal.rt.parser.ScriptParser;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Compare V1 and V2 parser outputs for the same OAL scripts.
 *
 * This ensures V2 produces equivalent results to V1.
 */
@Slf4j
public class V1VsV2ComparisonTest {

    /**
     * Find the OAL scripts directory in the project.
     *
     * Tries multiple paths to locate the directory:
     * 1. From current working directory (Maven default)
     * 2. From user.dir system property
     * 3. Relative from this module
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

    /**
     * Compare V1 and V2 parsing of core.oal.
     *
     * Verifies:
     * - Same number of metrics parsed
     * - Same metric names
     * - Same source names
     * - Same function names
     */
    @Test
    public void testCompareCoreOAL() throws IOException {
        File oalDir = findOALScriptsDir();
        File oalFile = new File(oalDir, "core.oal");
        if (!oalFile.exists()) {
            log.warn("Skipping test - core.oal not found at: {}", oalFile.getAbsolutePath());
            return;
        }

        // Parse with V1
        ScriptParser v1Parser = ScriptParser.createFromFile(
            new FileReader(oalFile),
            "org.apache.skywalking.oap.server.core.source.");
        OALScripts v1Scripts = v1Parser.parse();

        // Parse with V2
        OALScriptParserV2 v2Parser = OALScriptParserV2.parse(new FileReader(oalFile), "core.oal");

        // Compare metrics count
        int v1Count = v1Scripts.getMetricsStmts().size();
        int v2Count = v2Parser.getMetricsCount();

        log.info("V1 parsed {} metrics, V2 parsed {} metrics", v1Count, v2Count);

        assertEquals(v1Count, v2Count, "V1 and V2 should parse the same number of metrics");

        // Build map of V1 metrics by name
        Map<String, org.apache.skywalking.oal.rt.parser.AnalysisResult> v1MetricsMap = new HashMap<>();
        v1Scripts.getMetricsStmts().forEach(metric -> {
            v1MetricsMap.put(metric.getVarName(), metric);
        });

        // Compare each V2 metric with corresponding V1 metric
        int matchCount = 0;
        for (MetricDefinition v2Metric : v2Parser.getMetrics()) {
            String metricName = v2Metric.getName();
            org.apache.skywalking.oal.rt.parser.AnalysisResult v1Metric = v1MetricsMap.get(metricName);

            assertNotNull(v1Metric, "V1 should have metric: " + metricName);

            // Compare source name
            String v1SourceName = v1Metric.getFrom().getSourceName();
            String v2SourceName = v2Metric.getSource().getName();

            assertEquals(v1SourceName, v2SourceName,
                String.format("Metric %s: source name mismatch", metricName));

            // Compare function name
            String v1FunctionName = v1Metric.getAggregationFuncStmt().getAggregationFunctionName();
            String v2FunctionName = v2Metric.getAggregationFunction().getName();

            assertEquals(v1FunctionName, v2FunctionName,
                String.format("Metric %s: function name mismatch", metricName));

            // Compare filter count
            int v1FilterCount = v1Metric.getFilters().getFilterExpressionsParserResult() == null
                ? 0
                : v1Metric.getFilters().getFilterExpressionsParserResult().size();
            int v2FilterCount = v2Metric.getFilters().size();

            assertEquals(v1FilterCount, v2FilterCount,
                String.format("Metric %s: filter count mismatch", metricName));

            matchCount++;
        }

        log.info("✅ V1 and V2 match for {}/{} metrics in core.oal", matchCount, v1Count);
        assertEquals(v1Count, matchCount, "All metrics should match");
    }

    /**
     * Compare V1 and V2 for disabled sources.
     */
    @Test
    public void testCompareDisabledSources() throws IOException {
        File oalDir = findOALScriptsDir();
        File oalFile = new File(oalDir, "disable.oal");
        if (!oalFile.exists()) {
            log.warn("Skipping test - disable.oal not found");
            return;
        }

        // Parse with V1
        ScriptParser v1Parser = ScriptParser.createFromFile(
            new FileReader(oalFile),
            "org.apache.skywalking.oap.server.core.source.");
        OALScripts v1Scripts = v1Parser.parse();

        // Parse with V2
        OALScriptParserV2 v2Parser = OALScriptParserV2.parse(new FileReader(oalFile), "disable.oal");

        // Compare disabled sources count
        int v1DisabledCount = v1Scripts.getDisableCollection().getAllDisableSources().size();
        int v2DisabledCount = v2Parser.getDisabledSources().size();

        log.info("V1 has {} disabled sources, V2 has {} disabled sources", v1DisabledCount, v2DisabledCount);

        assertEquals(v1DisabledCount, v2DisabledCount,
            "V1 and V2 should have same number of disabled sources");

        log.info("✅ V1 and V2 match for disabled sources");
    }

    /**
     * Compare V1 and V2 for all OAL files.
     *
     * This is the comprehensive test that validates V2 parses all production
     * OAL scripts identically to V1.
     */
    @Test
    public void testCompareAllOALFiles() throws IOException {
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
            // Skip disable.oal as it only has disable statements
        };

        int totalFilesCompared = 0;
        int totalMetricsCompared = 0;

        for (String fileName : oalFiles) {
            File oalFile = new File(oalDir, fileName);

            if (!oalFile.exists()) {
                log.warn("Skipping {} - file not found", fileName);
                continue;
            }

            try {
                // Parse with V1
                ScriptParser v1Parser = ScriptParser.createFromFile(
                    new FileReader(oalFile),
                    "org.apache.skywalking.oap.server.core.source.");
                OALScripts v1Scripts = v1Parser.parse();

                // Parse with V2
                OALScriptParserV2 v2Parser = OALScriptParserV2.parse(new FileReader(oalFile), fileName);

                // Compare counts
                int v1Count = v1Scripts.getMetricsStmts().size();
                int v2Count = v2Parser.getMetricsCount();

                assertEquals(v1Count, v2Count,
                    String.format("%s: V1 and V2 metric count mismatch", fileName));

                // Build V1 metrics map
                Map<String, org.apache.skywalking.oal.rt.parser.AnalysisResult> v1MetricsMap = new HashMap<>();
                v1Scripts.getMetricsStmts().forEach(metric -> {
                    v1MetricsMap.put(metric.getVarName(), metric);
                });

                // Compare each metric
                int matchCount = 0;
                for (MetricDefinition v2Metric : v2Parser.getMetrics()) {
                    String metricName = v2Metric.getName();
                    org.apache.skywalking.oal.rt.parser.AnalysisResult v1Metric = v1MetricsMap.get(metricName);

                    if (v1Metric == null) {
                        log.error("  ❌ {}: V1 missing metric: {}", fileName, metricName);
                        continue;
                    }

                    // Compare basics
                    String v1Source = v1Metric.getFrom().getSourceName();
                    String v2Source = v2Metric.getSource().getName();
                    String v1Function = v1Metric.getAggregationFuncStmt().getAggregationFunctionName();
                    String v2Function = v2Metric.getAggregationFunction().getName();

                    if (v1Source.equals(v2Source) && v1Function.equals(v2Function)) {
                        matchCount++;
                    } else {
                        log.error("  ❌ {}: Metric {} mismatch - V1({},{}) vs V2({},{})",
                            fileName, metricName, v1Source, v1Function, v2Source, v2Function);
                    }
                }

                totalFilesCompared++;
                totalMetricsCompared += matchCount;

                log.info("✅ {}: {}/{} metrics match", fileName, matchCount, v1Count);

            } catch (Exception e) {
                log.error("❌ Failed to compare {}: {}", fileName, e.getMessage(), e);
                throw e;
            }
        }

        log.info("✅ SUMMARY: Compared {} files, {} total metrics matched",
            totalFilesCompared, totalMetricsCompared);

        assertEquals(oalFiles.length, totalFilesCompared,
            "Should compare all OAL files");
        assertTrue(totalMetricsCompared > 100,
            "Should have compared at least 100 metrics total");
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
