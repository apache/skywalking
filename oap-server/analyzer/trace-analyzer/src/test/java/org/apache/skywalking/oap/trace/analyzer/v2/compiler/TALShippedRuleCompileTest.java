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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.trace.analyzer.v2.ShippedRules;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalExpression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped rules from {@code tal/otlp-default.yaml}, compiled end to end. They are the hardest input the
 * language has to take, so a regression anywhere in the grammar, the parser or the codegen shows up here.
 */
class TALShippedRuleCompileTest {

    /**
     * The metrics the design commits to, in the order the rule declares them: the sixteen on the reporting
     * service and its edges, plus three per conjectured peer kind so each virtual layer's list has columns.
     */
    private static final List<String> EXPECTED_METRICS = List.of(
        "otlp_service_cpm",
        "otlp_service_resp_time",
        "otlp_service_sla",
        "otlp_service_apdex",
        "otlp_service_percentile",
        "otlp_endpoint_cpm",
        "otlp_endpoint_resp_time",
        "otlp_endpoint_sla",
        "otlp_endpoint_percentile",
        "otlp_service_instance_cpm",
        "otlp_service_instance_resp_time",
        "otlp_service_instance_sla",
        "otlp_service_relation_server_cpm",
        "otlp_database_access_cpm",
        "otlp_database_access_resp_time",
        "otlp_database_access_sla",
        "otlp_cache_access_cpm",
        "otlp_cache_access_resp_time",
        "otlp_cache_access_sla",
        "otlp_mq_access_cpm",
        "otlp_mq_access_resp_time",
        "otlp_mq_access_sla",
        "otlp_mq_destination_cpm", "otlp_mq_destination_resp_time", "otlp_mq_destination_sla",
        "otlp_service_relation_client_cpm",
        "otlp_service_relation_client_resp_time",
        "otlp_service_relation_client_call_sla");

    @Test
    void compileTheShippedServiceRule() throws Exception {
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("otlp_service");
        generator.setMetricPrefix("otlp");
        generator.setSourceRef(ShippedRules.ref("otlp-service"));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));

        final TalExpression expression = generator.compile(ShippedRules.dsl("otlp-service"));
        assertNotNull(expression);

        final List<String> names = generator.getMetricDeclarations().stream()
                                            .map(d -> d.fullName)
                                            .collect(Collectors.toList());
        assertEquals(EXPECTED_METRICS, names);
    }

    @Test
    void theShippedServiceRuleUsesTheAgentFunctions() throws Exception {
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("otlp_service_functions");
        generator.setMetricPrefix("otlp");
        generator.setSourceRef(ShippedRules.ref("otlp-service"));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        generator.compile(ShippedRules.dsl("otlp-service"));

        // cpm maps to sumPerMin, which is the same arithmetic CPMMetrics does for the agent metrics, so the
        // values are comparable with service_cpm.
        assertEquals("sumPerMin", functionOf(generator, "otlp_service_cpm"));
        assertEquals("avg", functionOf(generator, "otlp_service_resp_time"));
        assertEquals("percent", functionOf(generator, "otlp_service_sla"));
        assertEquals("apdex", functionOf(generator, "otlp_service_apdex"));
        assertEquals("sumHistogramPercentile", functionOf(generator, "otlp_service_percentile"));
        assertEquals("SERVICE_RELATION", scopeOf(generator, "otlp_service_relation_client_cpm"));
        assertEquals("ENDPOINT", scopeOf(generator, "otlp_endpoint_cpm"));
        assertEquals("SERVICE_INSTANCE", scopeOf(generator, "otlp_service_instance_cpm"));
    }

    @Test
    void compileTheShippedSlowSqlRule() throws Exception {
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("otlp_slow_sql");
        generator.setMetricPrefix("otlp");
        generator.setSourceRef(ShippedRules.ref("otlp-slow-sql"));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        generator.setOutputType(TestSlowStatementBuilder.class);

        assertNotNull(generator.compile(ShippedRules.dsl("otlp-slow-sql")));
        assertTrue(generator.getMetricDeclarations().isEmpty(),
            "the slow-statement rule writes a record, not metrics");
    }

    private static String functionOf(final TALClassGenerator generator, final String name) {
        return generator.getMetricDeclarations().stream()
                        .filter(d -> d.fullName.equals(name))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no metric " + name))
                        .function;
    }

    private static String scopeOf(final TALClassGenerator generator, final String name) {
        return generator.getMetricDeclarations().stream()
                        .filter(d -> d.fullName.equals(name))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no metric " + name))
                        .scopeType;
    }

}
