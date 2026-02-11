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

package org.apache.skywalking.oal.v2.parser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test V2 parser with real OAL scripts from server-starter/resources/oal.
 *
 * This validates that V2 can parse all production OAL scripts.
 */
@Slf4j
public class RealOALScriptsTest {

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
     * Test parsing core.oal with V2.
     *
     * core.oal contains the main service and endpoint metrics.
     */
    @Test
    public void testParseCoreOAL() throws IOException {
        File oalDir = findOALScriptsDir();
        File oalFile = new File(oalDir, "core.oal");
        assertTrue(oalFile.exists(), "core.oal not found at: " + oalFile.getAbsolutePath());

        OALScriptParserV2 parser = OALScriptParserV2.parse(new FileReader(oalFile), "core.oal");

        assertNotNull(parser);
        assertTrue(parser.hasMetrics(), "core.oal should have metrics");

        log.info("✅ Parsed core.oal: {} metrics", parser.getMetricsCount());

        // Verify some expected metrics exist
        boolean foundServiceRespTime = false;
        boolean foundServiceSla = false;
        boolean foundServiceCpm = false;

        for (MetricDefinition metric : parser.getMetrics()) {
            String name = metric.getName();
            if (name.equals("service_resp_time")) {
                foundServiceRespTime = true;
                log.info("  - Found: service_resp_time (source: {}, function: {})",
                    metric.getSource().getName(),
                    metric.getAggregationFunction().getName());
            } else if (name.equals("service_sla")) {
                foundServiceSla = true;
            } else if (name.equals("service_cpm")) {
                foundServiceCpm = true;
            }
        }

        assertTrue(foundServiceRespTime, "Should find service_resp_time");
        assertTrue(foundServiceSla, "Should find service_sla");
        assertTrue(foundServiceCpm, "Should find service_cpm");
    }

    /**
     * Test parsing all OAL files from server-starter/resources/oal.
     */
    @Test
    public void testParseAllOALFiles() throws IOException {
        File oalDir = findOALScriptsDir();
        String[] oalFiles = {
            "core.oal",
            "java-agent.oal",
            "dotnet-agent.oal",
            "browser.oal",
            "mesh.oal",
            "ebpf.oal",
            "tcp.oal",
            "cilium.oal",
            "disable.oal"
        };

        int totalMetrics = 0;
        int successCount = 0;

        for (String fileName : oalFiles) {
            File oalFile = new File(oalDir, fileName);
            assertTrue(oalFile.exists(), fileName + " not found at: " + oalFile.getAbsolutePath());

            try {
                OALScriptParserV2 parser = OALScriptParserV2.parse(new FileReader(oalFile), fileName);
                int metricsCount = parser.getMetricsCount();
                totalMetrics += metricsCount;
                successCount++;

                log.info("✅ Parsed {}: {} metrics, {} disabled sources",
                    fileName,
                    metricsCount,
                    parser.getDisabledSources().size());

            } catch (Exception e) {
                log.error("❌ Failed to parse {}: {}", fileName, e.getMessage(), e);
                throw e;
            }
        }

        log.info("✅ Successfully parsed {}/{} OAL files, total {} metrics",
            successCount, oalFiles.length, totalMetrics);

        assertTrue(successCount >= 5, "Should successfully parse at least 5 OAL files");
        assertTrue(totalMetrics > 50, "Should have at least 50 metrics total");
    }

    /**
     * Test that V2 parser handles comments correctly.
     */
    @Test
    public void testCommentsInRealOAL() throws IOException {
        File oalDir = findOALScriptsDir();
        File oalFile = new File(oalDir, "core.oal");
        if (!oalFile.exists()) {
            log.warn("Skipping test - core.oal not found");
            return;
        }

        // core.oal has line comments like: // Multiple values including p50, p75, p90, p95, p99
        OALScriptParserV2 parser = OALScriptParserV2.parse(new FileReader(oalFile), "core.oal");

        assertNotNull(parser);
        assertTrue(parser.hasMetrics());

        log.info("✅ V2 parser correctly handles comments in OAL scripts");
    }

    /**
     * Test parsing OAL with decorators (real feature in core.oal).
     */
    @Test
    public void testDecoratorsInRealOAL() throws IOException {
        File oalDir = findOALScriptsDir();
        File oalFile = new File(oalDir, "core.oal");
        if (!oalFile.exists()) {
            log.warn("Skipping test - core.oal not found");
            return;
        }

        OALScriptParserV2 parser = OALScriptParserV2.parse(new FileReader(oalFile), "core.oal");

        // core.oal has: service_resp_time = from(Service.latency).longAvg().decorator("ServiceDecorator");
        boolean foundDecorator = false;
        for (MetricDefinition metric : parser.getMetrics()) {
            if (metric.getDecorator().isPresent()) {
                foundDecorator = true;
                log.info("  - Found decorator: {} in metric: {}",
                    metric.getDecorator().get(),
                    metric.getName());
                break;
            }
        }

        assertTrue(foundDecorator, "Should find at least one metric with decorator");
        log.info("✅ V2 parser correctly handles decorators");
    }

    /**
     * Test parsing OAL with complex filters (DetectPoint, RequestType enums).
     */
    @Test
    public void testComplexFiltersInRealOAL() throws IOException {
        File oalDir = findOALScriptsDir();
        File oalFile = new File(oalDir, "core.oal");
        if (!oalFile.exists()) {
            log.warn("Skipping test - core.oal not found");
            return;
        }

        OALScriptParserV2 parser = OALScriptParserV2.parse(new FileReader(oalFile), "core.oal");

        // core.oal has: from(ServiceRelation.*).filter(detectPoint == DetectPoint.CLIENT)
        boolean foundComplexFilter = false;
        for (MetricDefinition metric : parser.getMetrics()) {
            if (!metric.getFilters().isEmpty()) {
                foundComplexFilter = true;
                log.info("  - Found filter in metric: {} ({} filters)",
                    metric.getName(),
                    metric.getFilters().size());
                break;
            }
        }

        assertTrue(foundComplexFilter, "Should find metrics with filters");
        log.info("✅ V2 parser correctly handles complex filters");
    }
}
