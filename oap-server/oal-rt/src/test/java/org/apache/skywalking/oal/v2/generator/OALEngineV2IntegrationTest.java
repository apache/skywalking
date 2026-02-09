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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for V2 engine - verifies that V2 can parse and enrich OAL scripts.
 */
public class OALEngineV2IntegrationTest {

    /**
     * Test V2 parser + enricher pipeline.
     *
     * Input OAL:
     *   service_resp_time = from(Service.latency).longAvg();
     *
     * Expected:
     *   - Parse succeeds
     *   - Enrichment succeeds
     *   - CodeGenModel is created with correct metadata
     */
    @Test
    public void testV2ParseAndEnrich() throws Exception {
        // Simple OAL script
        String oal = "service_resp_time = from(Service.latency).longAvg();";

        // Parse with V2
        OALScriptParserV2 parser = OALScriptParserV2.parse(new StringReader(oal), "test.oal");

        assertEquals(1, parser.getMetricsCount());

        MetricDefinition metric = parser.getMetrics().get(0);
        assertEquals("service_resp_time", metric.getName());
        assertEquals("Service", metric.getSource().getName());
        assertEquals("longAvg", metric.getAggregationFunction().getName());

        // Enrich with V2
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(
            "org.apache.skywalking.oap.server.core.source.",
            "org.apache.skywalking.oap.server.core.source.oal.rt.metrics."
        );

        CodeGenModel model = enricher.enrich(metric);

        // Verify enrichment
        assertNotNull(model);
        assertEquals("service_resp_time", model.getVarName());
        assertEquals("ServiceRespTime", model.getMetricsName());
        assertEquals("Service", model.getSourceName());
        assertEquals("longAvg", model.getFunctionName());

        // Verify source fields were extracted
        assertTrue(model.getFieldsFromSource().size() > 0);

        // Verify entrance method was built
        assertNotNull(model.getEntranceMethod());
        assertEquals("combine", model.getEntranceMethod().getMethodName());
    }

    /**
     * Test V2 with filter expressions.
     */
    @Test
    public void testV2WithFilters() throws Exception {
        String oal = "service_resp_time = from(Service.latency).filter(latency > 100).longAvg();";

        OALScriptParserV2 parser = OALScriptParserV2.parse(new StringReader(oal), "test.oal");
        MetricDefinition metric = parser.getMetrics().get(0);

        assertEquals(1, metric.getFilters().size());

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(
            "org.apache.skywalking.oap.server.core.source.",
            "org.apache.skywalking.oap.server.core.source.oal.rt.metrics."
        );

        CodeGenModel model = enricher.enrich(metric);
        assertNotNull(model);
        assertEquals(1, model.getFilters().size());
    }

    /**
     * Test V2 with multiple metrics.
     */
    @Test
    public void testV2MultipleMetrics() throws Exception {
        String oal = "service_resp_time = from(Service.latency).longAvg();\n" +
            "service_calls = from(Service.*).count();";

        OALScriptParserV2 parser = OALScriptParserV2.parse(new StringReader(oal), "test.oal");

        assertEquals(2, parser.getMetricsCount());

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(
            "org.apache.skywalking.oap.server.core.source.",
            "org.apache.skywalking.oap.server.core.source.oal.rt.metrics."
        );

        List<CodeGenModel> models = new ArrayList<>();
        for (MetricDefinition metric : parser.getMetrics()) {
            CodeGenModel model = enricher.enrich(metric);
            assertNotNull(model);
            models.add(model);
        }

        assertEquals(2, models.size());
        assertEquals("ServiceRespTime", models.get(0).getMetricsName());
        assertEquals("ServiceCalls", models.get(1).getMetricsName());
    }
}
