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

import java.io.IOException;
import org.apache.skywalking.oal.v2.model.FilterOperator;
import org.apache.skywalking.oal.v2.model.FunctionArgument;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OALScriptParserV2Test {

    /**
     * Test parsing simple metric with source attribute and aggregation function.
     *
     * Input OAL:
     *   service_resp_time = from(Service.latency).longAvg();
     *
     * Expected Output (YAML):
     *   MetricDefinition:
     *     name: "service_resp_time"
     *     tableName: "service_resp_time"
     *     source:
     *       name: "Service"
     *       attributes: ["latency"]
     *       wildcard: false
     *     aggregationFunction:
     *       name: "longAvg"
     *       arguments: []
     *     filters: []
     *     decorator: null
     */
    @Test
    public void testSimpleAverage() throws IOException {
        String oal = "service_resp_time = from(Service.latency).longAvg();";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        assertTrue(parser.hasMetrics());
        assertEquals(1, parser.getMetricsCount());

        MetricDefinition metric = parser.getMetrics().get(0);
        assertEquals("service_resp_time", metric.getName());
        assertEquals("Service", metric.getSource().getName());
        assertEquals(1, metric.getSource().getAttributes().size());
        assertEquals("latency", metric.getSource().getAttributes().get(0));
        assertEquals("longAvg", metric.getAggregationFunction().getName());
        assertTrue(metric.getFilters().isEmpty());
        assertFalse(metric.getDecorator().isPresent());
    }

    /**
     * Test parsing metric with wildcard source (.*).
     *
     * Input OAL:
     *   service_calls = from(Service.*).count();
     *
     * Expected Output (YAML):
     *   MetricDefinition:
     *     name: "service_calls"
     *     source:
     *       name: "Service"
     *       wildcard: true
     *       attributes: []
     *     aggregationFunction:
     *       name: "count"
     *       arguments: []
     */
    @Test
    public void testWildcardSource() throws IOException {
        String oal = "service_calls = from(Service.*).count();";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        assertEquals(1, parser.getMetricsCount());
        MetricDefinition metric = parser.getMetrics().get(0);

        assertEquals("service_calls", metric.getName());
        assertEquals("Service", metric.getSource().getName());
        assertTrue(metric.getSource().isWildcard());
        assertEquals("count", metric.getAggregationFunction().getName());
    }

    /**
     * Test parsing metric with numeric filter (greater than).
     *
     * Input OAL:
     *   service_slow = from(Service.latency).filter(latency > 1000).longAvg();
     *
     * Expected Output (YAML):
     *   MetricDefinition:
     *     name: "service_slow"
     *     source:
     *       name: "Service"
     *       attributes: ["latency"]
     *     filters:
     *       - fieldName: "latency"
     *         operator: GREATER
     *         value: {type: NUMBER, value: 1000}
     *     aggregationFunction:
     *       name: "longAvg"
     */
    @Test
    public void testWithNumberFilter() throws IOException {
        String oal = "service_slow = from(Service.latency).filter(latency > 1000).longAvg();";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        MetricDefinition metric = parser.getMetrics().get(0);
        assertEquals("service_slow", metric.getName());
        assertEquals(1, metric.getFilters().size());

        assertEquals("latency", metric.getFilters().get(0).getFieldName());
        assertEquals(FilterOperator.GREATER, metric.getFilters().get(0).getOperator());
        assertEquals(1000L, metric.getFilters().get(0).getValue().asLong());
    }

    /**
     * Test parsing metric with boolean filter.
     *
     * Input OAL:
     *   endpoint_success = from(Endpoint.*).filter(status == true).percent();
     *
     * Expected Output (YAML):
     *   MetricDefinition:
     *     name: "endpoint_success"
     *     source:
     *       name: "Endpoint"
     *       wildcard: true
     *     filters:
     *       - fieldName: "status"
     *         operator: EQUAL
     *         value: {type: BOOLEAN, value: true}
     *     aggregationFunction:
     *       name: "percent"
     */
    @Test
    public void testWithBooleanFilter() throws IOException {
        String oal = "endpoint_success = from(Endpoint.*).filter(status == true).percent();";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        MetricDefinition metric = parser.getMetrics().get(0);
        assertEquals(1, metric.getFilters().size());

        assertEquals("status", metric.getFilters().get(0).getFieldName());
        assertEquals(FilterOperator.EQUAL, metric.getFilters().get(0).getOperator());
        assertTrue(metric.getFilters().get(0).getValue().asBoolean());
    }

    /**
     * Test parsing metric with string filter (LIKE operator).
     *
     * Input OAL:
     *   service_name_match = from(Service.*).filter(name like "serv%").count();
     *
     * Expected Output (YAML):
     *   MetricDefinition:
     *     name: "service_name_match"
     *     source:
     *       name: "Service"
     *       wildcard: true
     *     filters:
     *       - fieldName: "name"
     *         operator: LIKE
     *         value: {type: STRING, value: "serv%"}
     *     aggregationFunction:
     *       name: "count"
     */
    @Test
    public void testWithStringFilter() throws IOException {
        String oal = "service_name_match = from(Service.*).filter(name like \"serv%\").count();";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        MetricDefinition metric = parser.getMetrics().get(0);
        assertEquals(1, metric.getFilters().size());

        assertEquals("name", metric.getFilters().get(0).getFieldName());
        assertEquals(FilterOperator.LIKE, metric.getFilters().get(0).getOperator());
        assertEquals("serv%", metric.getFilters().get(0).getValue().asString());
    }

    /**
     * Test parsing metric with multiple chained filters.
     *
     * Input OAL:
     *   endpoint_filtered = from(Endpoint.latency)
     *     .filter(latency > 100)
     *     .filter(status == true)
     *     .longAvg();
     *
     * Expected Output (YAML):
     *   MetricDefinition:
     *     name: "endpoint_filtered"
     *     source:
     *       name: "Endpoint"
     *       attributes: ["latency"]
     *     filters:
     *       - fieldName: "latency"
     *         operator: GREATER
     *         value: {type: NUMBER, value: 100}
     *       - fieldName: "status"
     *         operator: EQUAL
     *         value: {type: BOOLEAN, value: true}
     *     aggregationFunction:
     *       name: "longAvg"
     */
    @Test
    public void testMultipleFilters() throws IOException {
        String oal = "endpoint_filtered = from(Endpoint.latency)" +
            ".filter(latency > 100)" +
            ".filter(status == true)" +
            ".longAvg();";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        MetricDefinition metric = parser.getMetrics().get(0);
        assertEquals(2, metric.getFilters().size());

        // First filter
        assertEquals("latency", metric.getFilters().get(0).getFieldName());
        assertEquals(FilterOperator.GREATER, metric.getFilters().get(0).getOperator());
        assertEquals(100L, metric.getFilters().get(0).getValue().asLong());

        // Second filter
        assertEquals("status", metric.getFilters().get(1).getFieldName());
        assertEquals(FilterOperator.EQUAL, metric.getFilters().get(1).getOperator());
        assertTrue(metric.getFilters().get(1).getValue().asBoolean());
    }

    /**
     * Test parsing metric with decorator.
     *
     * Input OAL:
     *   service_resp_time = from(Service.latency)
     *     .longAvg()
     *     .decorator("ServiceDecorator");
     *
     * Expected Output (YAML):
     *   MetricDefinition:
     *     name: "service_resp_time"
     *     source:
     *       name: "Service"
     *       attributes: ["latency"]
     *     aggregationFunction:
     *       name: "longAvg"
     *     decorator: "ServiceDecorator"
     */
    @Test
    public void testWithDecorator() throws IOException {
        String oal = "service_resp_time = from(Service.latency)" +
            ".longAvg()" +
            ".decorator(\"ServiceDecorator\");";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        MetricDefinition metric = parser.getMetrics().get(0);
        assertTrue(metric.getDecorator().isPresent());
        assertEquals("ServiceDecorator", metric.getDecorator().get());
    }

    /**
     * Test parsing function with single literal argument.
     *
     * Input OAL:
     *   service_percentile = from(Service.latency).percentile2(10);
     *
     * Expected Output (YAML):
     *   MetricDefinition:
     *     name: "service_percentile"
     *     source:
     *       name: "Service"
     *       attributes: ["latency"]
     *     aggregationFunction:
     *       name: "percentile2"
     *       arguments:
     *         - type: LITERAL
     *           value: 10
     */
    @Test
    public void testFunctionWithArguments() throws IOException {
        String oal = "service_percentile = from(Service.latency).percentile2(10);";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        MetricDefinition metric = parser.getMetrics().get(0);
        assertEquals("percentile2", metric.getAggregationFunction().getName());
        assertEquals(1, metric.getAggregationFunction().getArguments().size());

        FunctionArgument arg = metric.getAggregationFunction().getArguments().get(0);
        assertTrue(arg.isLiteral());
        assertEquals(10L, arg.asLiteral());
    }

    /**
     * Test parsing function with multiple literal arguments.
     *
     * Input OAL:
     *   service_histogram = from(Service.latency).histogram(100, 20);
     *
     * Expected Output (YAML):
     *   MetricDefinition:
     *     name: "service_histogram"
     *     source:
     *       name: "Service"
     *       attributes: ["latency"]
     *     aggregationFunction:
     *       name: "histogram"
     *       arguments:
     *         - type: LITERAL
     *           value: 100
     *         - type: LITERAL
     *           value: 20
     */
    @Test
    public void testFunctionWithMultipleArguments() throws IOException {
        String oal = "service_histogram = from(Service.latency).histogram(100, 20);";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        MetricDefinition metric = parser.getMetrics().get(0);
        assertEquals("histogram", metric.getAggregationFunction().getName());
        assertEquals(2, metric.getAggregationFunction().getArguments().size());

        assertEquals(100L, metric.getAggregationFunction().getArguments().get(0).asLiteral());
        assertEquals(20L, metric.getAggregationFunction().getArguments().get(1).asLiteral());
    }

    /**
     * Test parsing multiple metrics in one OAL script.
     *
     * Input OAL:
     *   service_resp_time = from(Service.latency).longAvg();
     *   service_calls = from(Service.*).count();
     *   endpoint_success = from(Endpoint.*).filter(status == true).percent();
     *
     * Expected Output (YAML):
     *   metrics:
     *     - name: "service_resp_time"
     *       source: {name: "Service", attributes: ["latency"]}
     *       aggregationFunction: {name: "longAvg"}
     *     - name: "service_calls"
     *       source: {name: "Service", wildcard: true}
     *       aggregationFunction: {name: "count"}
     *     - name: "endpoint_success"
     *       source: {name: "Endpoint", wildcard: true}
     *       filters: [{fieldName: "status", operator: EQUAL, value: true}]
     *       aggregationFunction: {name: "percent"}
     */
    @Test
    public void testMultipleMetrics() throws IOException {
        String oal = "service_resp_time = from(Service.latency).longAvg();\n" +
            "service_calls = from(Service.*).count();\n" +
            "endpoint_success = from(Endpoint.*).filter(status == true).percent();\n";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        assertEquals(3, parser.getMetricsCount());

        assertEquals("service_resp_time", parser.getMetrics().get(0).getName());
        assertEquals("service_calls", parser.getMetrics().get(1).getName());
        assertEquals("endpoint_success", parser.getMetrics().get(2).getName());
    }

    /**
     * Test parsing disable statement along with metrics.
     *
     * Input OAL:
     *   service_resp_time = from(Service.latency).longAvg();
     *   disable(segment);
     *
     * Expected Output (YAML):
     *   metrics:
     *     - name: "service_resp_time"
     *   disabledSources:
     *     - "segment"
     */
    @Test
    public void testDisableStatement() throws IOException {
        String oal = "service_resp_time = from(Service.latency).longAvg();\n" +
            "disable(segment);\n";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        assertEquals(1, parser.getMetricsCount());
        assertTrue(parser.hasDisabledSources());
        assertEquals(1, parser.getDisabledSources().size());
        assertEquals("segment", parser.getDisabledSources().get(0));
    }

    /**
     * Test parsing complex real-world OAL script with multiple metrics and decorators.
     *
     * Input OAL:
     *   // Service scope metrics
     *   service_resp_time = from(Service.latency).longAvg().decorator("ServiceDecorator");
     *   service_sla = from(Service.*).percent(status == true).decorator("ServiceDecorator");
     *   service_cpm = from(Service.*).cpm().decorator("ServiceDecorator");
     *   service_percentile = from(Service.latency).percentile2(10);
     *
     * Expected Output (YAML):
     *   metrics:
     *     - name: "service_resp_time"
     *       aggregationFunction: {name: "longAvg"}
     *       decorator: "ServiceDecorator"
     *     - name: "service_sla"
     *       aggregationFunction: {name: "percent"}
     *       decorator: "ServiceDecorator"
     *     - name: "service_cpm"
     *       aggregationFunction: {name: "cpm"}
     *       decorator: "ServiceDecorator"
     *     - name: "service_percentile"
     *       aggregationFunction: {name: "percentile2", arguments: [{type: LITERAL, value: 10}]}
     */
    @Test
    public void testComplexRealWorldExample() throws IOException {
        String oal = "// Service scope metrics\n" +
            "service_resp_time = from(Service.latency).longAvg().decorator(\"ServiceDecorator\");\n" +
            "service_sla = from(Service.*).percent(status == true).decorator(\"ServiceDecorator\");\n" +
            "service_cpm = from(Service.*).cpm().decorator(\"ServiceDecorator\");\n" +
            "service_percentile = from(Service.latency).percentile2(10);\n";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        assertEquals(4, parser.getMetricsCount());

        // Verify first metric
        MetricDefinition metric1 = parser.getMetrics().get(0);
        assertEquals("service_resp_time", metric1.getName());
        assertEquals("longAvg", metric1.getAggregationFunction().getName());
        assertTrue(metric1.getDecorator().isPresent());
        assertEquals("ServiceDecorator", metric1.getDecorator().get());

        // Verify second metric with filter in function argument
        MetricDefinition metric2 = parser.getMetrics().get(1);
        assertEquals("service_sla", metric2.getName());
        assertEquals("percent", metric2.getAggregationFunction().getName());
    }

    /**
     * Test source location tracking for error reporting.
     *
     * Input OAL:
     *   service_resp_time = from(Service.latency).longAvg();
     *
     * Input fileName: "test.oal"
     *
     * Expected Output (YAML):
     *   MetricDefinition:
     *     name: "service_resp_time"
     *     location:
     *       fileName: "test.oal"
     *       line: 1
     *       column: 0
     */
    @Test
    public void testSourceLocation() throws IOException {
        String oal = "service_resp_time = from(Service.latency).longAvg();";

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal, "test.oal");

        MetricDefinition metric = parser.getMetrics().get(0);
        assertEquals("test.oal", metric.getLocation().getFileName());
        assertEquals(1, metric.getLocation().getLine());
    }

    /**
     * Test all comparison operators (>, <, >=, <=).
     *
     * Input OAL Cases:
     *   1. filter(latency > 100)
     *   2. filter(latency < 100)
     *   3. filter(latency >= 100)
     *   4. filter(latency <= 100)
     *
     * Expected Operators:
     *   1. GREATER (>)
     *   2. LESS (<)
     *   3. GREATER_EQUAL (>=)
     *   4. LESS_EQUAL (<=)
     */
    @Test
    public void testComparisionOperators() throws IOException {
        OALScriptParserV2 parser = OALScriptParserV2.parse(
            "m1 = from(Service.*).filter(latency > 100).count();"
        );
        assertEquals(FilterOperator.GREATER, parser.getMetrics().get(0).getFilters().get(0).getOperator());

        parser = OALScriptParserV2.parse(
            "m2 = from(Service.*).filter(latency < 100).count();"
        );
        assertEquals(FilterOperator.LESS, parser.getMetrics().get(0).getFilters().get(0).getOperator());

        parser = OALScriptParserV2.parse(
            "m3 = from(Service.*).filter(latency >= 100).count();"
        );
        assertEquals(FilterOperator.GREATER_EQUAL, parser.getMetrics().get(0).getFilters().get(0).getOperator());

        parser = OALScriptParserV2.parse(
            "m4 = from(Service.*).filter(latency <= 100).count();"
        );
        assertEquals(FilterOperator.LESS_EQUAL, parser.getMetrics().get(0).getFilters().get(0).getOperator());
    }

    /**
     * Test parsing empty OAL script.
     *
     * Input OAL:
     *   (empty string)
     *
     * Expected Output (YAML):
     *   metrics: []
     *   disabledSources: []
     *   hasMetrics: false
     *   metricsCount: 0
     */
    @Test
    public void testEmptyScript() throws IOException {
        OALScriptParserV2 parser = OALScriptParserV2.parse("");

        assertFalse(parser.hasMetrics());
        assertEquals(0, parser.getMetricsCount());
    }
}
