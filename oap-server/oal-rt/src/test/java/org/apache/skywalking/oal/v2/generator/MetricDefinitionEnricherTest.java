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

import java.util.List;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.Endpoint;
import org.apache.skywalking.oap.server.core.source.K8SService;
import org.apache.skywalking.oap.server.core.source.Service;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
import org.apache.skywalking.oap.server.core.source.TCPService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MetricDefinitionEnricher.
 *
 * These tests verify the enricher correctly handles edge cases:
 * - Map expression handling (tag["key"])
 * - Array value handling for `in` operator
 * - Nested boolean fields (connect.success)
 * - Type casting for @Arg parameters
 * - Boolean accessor (isStatus vs getStatus)
 */
public class MetricDefinitionEnricherTest {

    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String METRICS_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.";

    @BeforeAll
    public static void initializeScopes() {
        try {
            DefaultScopeDefine.Listener listener = new DefaultScopeDefine.Listener();
            listener.notify(Service.class);
            listener.notify(Endpoint.class);
            listener.notify(ServiceRelation.class);
            listener.notify(K8SService.class);
            listener.notify(TCPService.class);
        } catch (RuntimeException e) {
            // Scopes may already be registered by other tests
        }
    }

    /**
     * Test map expression in filter: tag["key"] != null
     * Should generate: source.getTag("key") instead of source.getTag["key"]()
     */
    @Test
    public void testMapExpressionInFilter() throws Exception {
        String oal = "test_metric = from(Service.latency).filter(tag[\"transmission.latency\"] != null).longAvg();";

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        // Verify filter expression is correctly processed
        List<CodeGenModel.FilterExpressionV2> filterExpressions = model.getFilterExpressions();
        assertEquals(1, filterExpressions.size());

        CodeGenModel.FilterExpressionV2 filterExpr = filterExpressions.get(0);
        // Verify left side uses getTag("transmission.latency") format, not getTag["..."]()
        assertNotNull(filterExpr.getLeft());
        assertTrue(filterExpr.getLeft().contains("source.getTag(\"transmission.latency\")"),
            "Expected source.getTag(\"transmission.latency\") but got: " + filterExpr.getLeft());
        assertFalse(filterExpr.getLeft().contains("["),
            "Should not contain [ in method call: " + filterExpr.getLeft());

        // Verify right side is null
        assertEquals("null", filterExpr.getRight());
    }

    /**
     * Test map expression in source attribute: from((str->long)Service.tag["key"])
     * Should work with type casting applied.
     */
    @Test
    public void testMapExpressionInSourceAttribute() throws Exception {
        String oal = "test_metric = from((str->long)Service.tag[\"transmission.latency\"])" +
            ".filter(type == RequestType.MQ).longAvg();";

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        // Verify entrance method args expression includes proper map access
        assertNotNull(model.getEntranceMethod());
        List<Object> argsExpressions = model.getEntranceMethod().getArgsExpressions();
        assertFalse(argsExpressions.isEmpty());

        // First arg should be the source value with type cast
        String sourceExpr = argsExpressions.get(0).toString();
        assertTrue(sourceExpr.contains("getTag(\"transmission.latency\")"),
            "Expected getTag(\"transmission.latency\") but got: " + sourceExpr);
        assertTrue(sourceExpr.contains("Long.parseLong") || sourceExpr.contains("str->long"),
            "Expected type cast but got: " + sourceExpr);
    }

    /**
     * Test array value in filter with `in` operator: filter(errorCategory in ["A", "B", "C"])
     * Should wrap values in new Object[]{...} format.
     */
    @Test
    public void testArrayValueInFilterWithInOperator() throws Exception {
        String oal = "test_metric = from(Service.*).filter(rpcStatusCode in [\"OK\", \"ERROR\", \"UNKNOWN\"]).count();";

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        List<CodeGenModel.FilterExpressionV2> filterExpressions = model.getFilterExpressions();
        assertEquals(1, filterExpressions.size());

        CodeGenModel.FilterExpressionV2 filterExpr = filterExpressions.get(0);

        // Verify left side references the field
        assertTrue(filterExpr.getLeft().contains("getRpcStatusCode"),
            "Expected getRpcStatusCode but got: " + filterExpr.getLeft());

        // Verify right side is wrapped in new Object[]{}
        String right = filterExpr.getRight();
        assertTrue(right.startsWith("new Object[]{"),
            "Expected new Object[]{...} but got: " + right);
        assertTrue(right.contains("\"OK\""), "Expected \"OK\" in array: " + right);
        assertTrue(right.contains("\"ERROR\""), "Expected \"ERROR\" in array: " + right);
        assertTrue(right.contains("\"UNKNOWN\""), "Expected \"UNKNOWN\" in array: " + right);
        assertTrue(right.endsWith("}"), "Expected to end with } but got: " + right);

        // Verify InMatch matcher is used
        assertTrue(filterExpr.getExpressionObject().contains("InMatch"),
            "Expected InMatch but got: " + filterExpr.getExpressionObject());
    }

    /**
     * Test boolean filter expression: filter(status == true)
     * Should use isStatus() instead of getStatus().
     */
    @Test
    public void testBooleanFilterExpression() throws Exception {
        String oal = "test_sla = from(Service.*).filter(status == true).percent(status == true);";

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        List<CodeGenModel.FilterExpressionV2> filterExpressions = model.getFilterExpressions();
        assertEquals(1, filterExpressions.size());

        CodeGenModel.FilterExpressionV2 filterExpr = filterExpressions.get(0);

        // Verify left side uses isStatus() for boolean field
        assertTrue(filterExpr.getLeft().contains("isStatus"),
            "Expected isStatus() but got: " + filterExpr.getLeft());
        assertFalse(filterExpr.getLeft().contains("getStatus"),
            "Should not use getStatus for boolean: " + filterExpr.getLeft());

        // Verify right side is true
        assertEquals("true", filterExpr.getRight());
    }

    /**
     * Test nested boolean field: filter(connect.success == true)
     * Should generate: source.getConnect().isSuccess()
     */
    @Test
    public void testNestedBooleanField() throws Exception {
        String oal = "k8s_service_connect_success = from(K8SService.*).filter(connect.success == true).count();";

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        List<CodeGenModel.FilterExpressionV2> filterExpressions = model.getFilterExpressions();
        assertEquals(1, filterExpressions.size());

        CodeGenModel.FilterExpressionV2 filterExpr = filterExpressions.get(0);

        // Verify left side uses nested getter with isSuccess at the end
        String left = filterExpr.getLeft();
        assertTrue(left.contains("source.getConnect()"),
            "Expected source.getConnect() but got: " + left);
        assertTrue(left.contains("isSuccess()"),
            "Expected isSuccess() for nested boolean but got: " + left);
        assertFalse(left.contains("getSuccess"),
            "Should not use getSuccess for boolean: " + left);

        // Verify right side is true
        assertEquals("true", filterExpr.getRight());
    }

    /**
     * Test type casting for @Arg parameters: apdex(name, status)
     * where status is boolean - should use isStatus() accessor.
     */
    @Test
    public void testBooleanArgAccessor() throws Exception {
        String oal = "service_apdex = from(Service.latency).apdex(name, status);";

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        // Verify entrance method args
        assertNotNull(model.getEntranceMethod());
        List<Object> argsExpressions = model.getEntranceMethod().getArgsExpressions();

        // Find the status argument (should use isStatus())
        boolean foundIsStatus = false;
        for (Object arg : argsExpressions) {
            String argStr = arg.toString();
            if (argStr.contains("isStatus")) {
                foundIsStatus = true;
            }
            // Should NOT use getStatus for boolean
            assertFalse(argStr.contains("getStatus"),
                "Should not use getStatus for boolean field: " + argStr);
        }
        assertTrue(foundIsStatus, "Expected isStatus() accessor for boolean argument");
    }

    /**
     * Test type casting for numeric @Arg parameters: labelAvg(name, duration)
     * where entrance method expects long but source field might be int.
     */
    @Test
    public void testNumericTypeCasting() throws Exception {
        String oal = "service_latency = from(Service.latency).longAvg();";

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        // Verify entrance method has proper type cast
        assertNotNull(model.getEntranceMethod());
        List<Object> argsExpressions = model.getEntranceMethod().getArgsExpressions();
        assertFalse(argsExpressions.isEmpty());

        // First arg should be the source value with type cast
        String sourceExpr = argsExpressions.get(0).toString();
        // The latency field is int but longAvg expects long, so should have cast
        assertTrue(sourceExpr.contains("(long)") || sourceExpr.contains("(int)"),
            "Expected type cast but got: " + sourceExpr);
    }

    /**
     * Test multiple filters in chain: filter(A).filter(B)
     * All filters should be correctly processed.
     */
    @Test
    public void testMultipleFiltersChain() throws Exception {
        String oal = "test_metric = from(Service.latency)" +
            ".filter(type == RequestType.MQ)" +
            ".filter(status == true)" +
            ".filter(tag[\"key\"] != null)" +
            ".longAvg();";

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        List<CodeGenModel.FilterExpressionV2> filterExpressions = model.getFilterExpressions();
        assertEquals(3, filterExpressions.size());

        // First filter: type == RequestType.MQ
        CodeGenModel.FilterExpressionV2 filter1 = filterExpressions.get(0);
        assertTrue(filter1.getLeft().contains("getType"),
            "First filter should reference type: " + filter1.getLeft());

        // Second filter: status == true (boolean)
        CodeGenModel.FilterExpressionV2 filter2 = filterExpressions.get(1);
        assertTrue(filter2.getLeft().contains("isStatus"),
            "Second filter should use isStatus: " + filter2.getLeft());

        // Third filter: tag["key"] != null (map expression)
        CodeGenModel.FilterExpressionV2 filter3 = filterExpressions.get(2);
        assertTrue(filter3.getLeft().contains("getTag(\"key\")"),
            "Third filter should use getTag(\"key\"): " + filter3.getLeft());
    }

    /**
     * Test numeric comparison operators: filter(latency > 1000)
     */
    @Test
    public void testNumericComparisonFilter() throws Exception {
        String oal = "slow_service = from(Service.latency).filter(latency > 1000).longAvg();";

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        List<CodeGenModel.FilterExpressionV2> filterExpressions = model.getFilterExpressions();
        assertEquals(1, filterExpressions.size());

        CodeGenModel.FilterExpressionV2 filterExpr = filterExpressions.get(0);

        // Verify GreaterMatch is used
        assertTrue(filterExpr.getExpressionObject().contains("GreaterMatch"),
            "Expected GreaterMatch but got: " + filterExpr.getExpressionObject());

        // Verify right side is numeric
        assertEquals("1000", filterExpr.getRight());
    }

    /**
     * Test enum comparison: filter(detectPoint == DetectPoint.CLIENT)
     */
    @Test
    public void testEnumComparisonFilter() throws Exception {
        String oal = "client_calls = from(ServiceRelation.*).filter(detectPoint == DetectPoint.CLIENT).count();";

        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        List<CodeGenModel.FilterExpressionV2> filterExpressions = model.getFilterExpressions();
        assertEquals(1, filterExpressions.size());

        CodeGenModel.FilterExpressionV2 filterExpr = filterExpressions.get(0);

        // Verify left side references detectPoint
        assertTrue(filterExpr.getLeft().contains("getDetectPoint"),
            "Expected getDetectPoint but got: " + filterExpr.getLeft());

        // Verify right side references enum value
        assertTrue(filterExpr.getRight().contains("DetectPoint.CLIENT"),
            "Expected DetectPoint.CLIENT but got: " + filterExpr.getRight());
    }
}
