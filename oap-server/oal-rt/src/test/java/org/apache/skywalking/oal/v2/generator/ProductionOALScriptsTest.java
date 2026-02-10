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

package org.apache.skywalking.oal.v2.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that verify all production OAL scripts can be parsed by the V2 parser.
 *
 * These OAL files are copied from server-starter/src/main/resources/oal/ to avoid
 * circular dependency issues.
 *
 * This test focuses on parsing validation. Full code generation integration testing
 * is handled by OALClassGeneratorV2Test and the E2E tests at runtime.
 */
public class ProductionOALScriptsTest {

    /**
     * Test that each production OAL file can be parsed correctly.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "core.oal",
        "java-agent.oal",
        "dotnet-agent.oal",
        "browser.oal",
        "tcp.oal",
        "mesh.oal",
        "ebpf.oal",
        "cilium.oal"
    })
    public void testProductionOALScriptParsing(String oalFileName) throws Exception {
        String oalContent = readOALFile(oalFileName);
        assertNotNull(oalContent, "OAL file should be readable: " + oalFileName);
        assertFalse(oalContent.isEmpty(), "OAL file should not be empty: " + oalFileName);

        try {
            // Parse the OAL script
            OALScriptParserV2 parser = OALScriptParserV2.parse(oalContent);
            List<MetricDefinition> metrics = parser.getMetrics();

            assertFalse(metrics.isEmpty(), "OAL file should define at least one metric: " + oalFileName);

            // Verify each metric has required fields
            for (MetricDefinition metric : metrics) {
                assertNotNull(metric.getName(), "Metric name should not be null in " + oalFileName);
                assertNotNull(metric.getSource(), "Metric source should not be null in " + oalFileName);
                assertNotNull(metric.getAggregationFunction(),
                    "Metric aggregation function should not be null in " + oalFileName);
            }
        } catch (Exception e) {
            fail("Failed to parse " + oalFileName + ": " + e.getMessage());
        }
    }

    /**
     * Test disable.oal can be parsed (all disable statements are currently commented out).
     */
    @Test
    public void testDisableOAL() throws Exception {
        String oalContent = readOALFile("disable.oal");
        assertNotNull(oalContent, "disable.oal should be readable");

        // Should parse without errors even with all statements commented out
        OALScriptParserV2 parser = OALScriptParserV2.parse(oalContent);
        // Currently all disable statements are commented out in disable.oal
        // so we just verify parsing succeeds
        assertNotNull(parser.getDisabledSources());
    }

    /**
     * Verify total metrics count across all OAL files.
     */
    @Test
    public void testTotalMetricsCount() throws Exception {
        String[] allFiles = {"core.oal", "java-agent.oal", "dotnet-agent.oal", "browser.oal",
            "tcp.oal", "mesh.oal", "ebpf.oal", "cilium.oal"};

        int totalMetrics = 0;
        for (String oalFileName : allFiles) {
            String oalContent = readOALFile(oalFileName);
            if (oalContent == null || oalContent.isEmpty()) {
                continue;
            }

            OALScriptParserV2 parser = OALScriptParserV2.parse(oalContent);
            totalMetrics += parser.getMetrics().size();
        }

        assertTrue(totalMetrics > 100,
            "Should have significant number of metrics across all OAL files, got: " + totalMetrics);
    }

    /**
     * Verify specific OAL syntax features are parsed correctly.
     */
    @Test
    public void testOALSyntaxFeatures() throws Exception {
        // Test nested property access (sideCar.internalRequestLatencyNanos)
        String meshContent = readOALFile("mesh.oal");
        if (meshContent != null) {
            OALScriptParserV2 parser = OALScriptParserV2.parse(meshContent);
            List<MetricDefinition> metrics = parser.getMetrics();

            boolean foundNestedAccess = metrics.stream()
                .anyMatch(m -> m.getSource().getAttributes().size() > 1 ||
                    m.getSource().getAttributes().stream()
                        .anyMatch(attr -> attr.contains(".")));
            assertTrue(foundNestedAccess, "mesh.oal should contain nested property access");
        }

        // Test map access (tag["key"])
        String coreContent = readOALFile("core.oal");
        if (coreContent != null) {
            OALScriptParserV2 parser = OALScriptParserV2.parse(coreContent);
            List<MetricDefinition> metrics = parser.getMetrics();

            boolean foundMapAccess = metrics.stream()
                .anyMatch(m -> m.getSource().getAttributes().stream()
                    .anyMatch(attr -> attr.contains("[")));
            assertTrue(foundMapAccess, "core.oal should contain map access syntax");
        }

        // Test cast type (str->long)
        if (coreContent != null) {
            OALScriptParserV2 parser = OALScriptParserV2.parse(coreContent);
            List<MetricDefinition> metrics = parser.getMetrics();

            boolean foundCastType = metrics.stream()
                .anyMatch(m -> m.getSource().getCastType().isPresent());
            assertTrue(foundCastType, "core.oal should contain cast type syntax");
        }
    }

    private String readOALFile(String fileName) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("oal/" + fileName)) {
            if (is == null) {
                return null;
            }
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }
}
