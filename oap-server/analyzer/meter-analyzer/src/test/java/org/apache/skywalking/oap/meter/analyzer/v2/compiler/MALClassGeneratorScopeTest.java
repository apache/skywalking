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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import javassist.ClassPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Full-expression compilation tests combining expPrefix + exp + expSuffix via formatExp.
 * Covers endpoint, service, instance, serviceRelation scope suffixes,
 * forEach closures with if/else-if/else chains, sum() with >10 label keys,
 * and tag() closure chained with service() suffix.
 */
class MALClassGeneratorScopeTest {

    private MALClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MALClassGenerator(new ClassPool(true));
    }

    /**
     * Simulates MetricConvert.formatExp() to build the full expression
     * from expPrefix + exp + expSuffix.
     */
    private static String formatExp(String expPrefix, String expSuffix, String exp) {
        String ret = exp;
        if (expPrefix != null && !expPrefix.isEmpty()) {
            int dotIdx = exp.indexOf('.');
            if (dotIdx > 0) {
                ret = String.format("(%s.%s)", exp.substring(0, dotIdx), expPrefix);
                String after = exp.substring(dotIdx + 1);
                if (!after.isEmpty()) {
                    ret = String.format("(%s.%s)", ret, after);
                }
            }
        }
        if (expSuffix != null && !expSuffix.isEmpty()) {
            ret = String.format("(%s).%s", ret, expSuffix);
        }
        return ret;
    }

    private void compileRule(String name, String expPrefix, String expSuffix, String exp)
            throws Exception {
        final String full = formatExp(expPrefix, expSuffix, exp);
        assertNotNull(generator.compile(name, full));
    }

    // --- Shared constants ---

    private static final String FOREACH_COMPONENT_PREFIX =
        "forEach(['component'], { key, tags ->\n"
        + "    String result = \"\"\n"
        + "    String protocol = tags['protocol']\n"
        + "    String ssl = tags['is_ssl']\n"
        + "    if (protocol == 'http' && ssl == 'true') {\n"
        + "      result = '129'\n"
        + "    } else if (protocol == 'http') {\n"
        + "      result = '49'\n"
        + "    } else if (ssl == 'true') {\n"
        + "      result = '130'\n"
        + "    } else {\n"
        + "      result = '110'\n"
        + "    }\n"
        + "    tags[key] = result\n"
        + "  })";

    private static final String RELATION_SERVER_SUFFIX =
        "serviceRelation(DetectPoint.SERVER, "
        + "[\"client_service_subset\", \"client_service_name\", \"client_namespace\", \"client_service_cluster\", \"client_service_env\"], "
        + "[\"server_service_subset\", \"server_service_name\", \"server_namespace\", \"server_service_cluster\", \"server_service_env\"], "
        + "'|', Layer.GENERAL, 'component')";

    private static final String RELATION_CLIENT_SUFFIX =
        "serviceRelation(DetectPoint.CLIENT, "
        + "[\"client_service_subset\", \"client_service_name\", \"client_namespace\", \"client_service_cluster\", \"client_service_env\"], "
        + "[\"server_service_subset\", \"server_service_name\", \"server_namespace\", \"server_service_cluster\", \"server_service_env\"], "
        + "'|', Layer.GENERAL, 'component')";

    private static final String HISTOGRAM_12_LABELS =
        ".sum([\"le\", \"client_service_subset\", \"client_service_name\", \"client_namespace\", "
        + "\"client_service_cluster\", \"client_service_env\", \"server_service_subset\", "
        + "\"server_service_name\", \"server_namespace\", \"server_service_cluster\", "
        + "\"server_service_env\", 'component'])"
        + ".downsampling(SUM).histogram().histogram_percentile([50, 75, 90, 95, 99])";

    // ==================== endpoint scope ====================

    @Test
    void endpointScopeWithHistogramPercentile() throws Exception {
        final String suffix = "endpoint([\"service_subset\", \"service_name\", \"k8s_namespace\", \"service_cluster\", \"service_env\"], [\"api\"], \"|\", Layer.GENERAL)";
        compileRule("ep_cpm", null, suffix,
            "api_agent_api_total_count.downsampling(SUM_PER_MIN)");
        compileRule("ep_duration", null, suffix,
            "api_agent_api_total_duration.downsampling(SUM_PER_MIN)");
        compileRule("ep_success", null, suffix,
            "api_agent_api_http_success_count.downsampling(SUM_PER_MIN)");
        compileRule("ep_percentile", null, suffix,
            "api_agent_api_response_time_histogram"
            + ".sum([\"le\", \"service_subset\", \"service_name\", \"k8s_namespace\", \"service_cluster\", \"service_env\", \"api\"])"
            + ".downsampling(SUM).histogram().histogram_percentile([50, 75, 90, 95, 99])");
    }

    // ==================== service scope ====================

    @Test
    void serviceScopeWithDelimiter() throws Exception {
        final String suffix = "service([\"service_subset\", \"service_name\", \"k8s_namespace\", \"service_cluster\", \"service_env\"], \"|\", Layer.GENERAL)";
        compileRule("svc_api_cpm", null, suffix,
            "api_agent_api_total_count.downsampling(SUM_PER_MIN)");
        compileRule("svc_api_duration", null, suffix,
            "api_agent_api_total_duration.downsampling(SUM_PER_MIN)");
        compileRule("svc_api_success", null, suffix,
            "api_agent_api_http_success_count.downsampling(SUM_PER_MIN)");
        compileRule("svc_api_percentile", null, suffix,
            "api_agent_api_response_time_histogram"
            + ".sum([\"le\", \"service_subset\", \"service_name\", \"k8s_namespace\", \"service_cluster\", \"service_env\"])"
            + ".downsampling(SUM).histogram().histogram_percentile([50, 75, 90, 95, 99])");
    }

    @Test
    void serviceScopeWithTcpMetrics() throws Exception {
        final String suffix = "service([\"service_subset\", \"service_name\", \"k8s_namespace\", \"service_cluster\", \"service_env\"], \"|\", Layer.GENERAL)";
        for (final String metric : new String[]{
                "write_bytes", "write_count", "write_execute_time",
                "read_bytes", "read_count", "read_execute_time",
                "connect_connection_count", "connect_connection_time",
                "accept_connection_count", "accept_connection_time",
                "close_connection_count", "close_connection_time"}) {
            compileRule("svc_tcp_" + metric, null, suffix,
                "metric_tcp_" + metric + ".downsampling(SUM_PER_MIN)");
        }
    }

    @Test
    void tagClosureChainedWithServiceSuffix() throws Exception {
        final String suffix = "tag({tags -> tags.service = 'satellite::' + tags.service}).service(['service'], Layer.SO11Y_SATELLITE)";
        compileRule("sat_receive", null, suffix,
            "sw_stl_gatherer_receive_count.sum([\"pipe\", \"status\", \"service\"]).increase(\"PT1M\")");
        compileRule("sat_fetch", null, suffix,
            "sw_stl_gatherer_fetch_count.sum([\"pipe\", \"status\", \"service\"]).increase(\"PT1M\")");
        compileRule("sat_queue_in", null, suffix,
            "sw_stl_queue_output_count.sum([\"pipe\", \"status\", \"service\"]).increase(\"PT1M\")");
        compileRule("sat_send", null, suffix,
            "sw_stl_sender_output_count.sum([\"pipe\", \"status\", \"service\"]).increase(\"PT1M\")");
        compileRule("sat_queue_cap", null, suffix,
            "sw_stl_pipeline_queue_total_capacity.sum([\"pipeline\", \"service\"])");
        compileRule("sat_queue_used", null, suffix,
            "sw_stl_pipeline_queue_partition_size.sum([\"pipeline\", \"service\"])");
        compileRule("sat_cpu", null, suffix,
            "sw_stl_grpc_server_cpu_gauge.downsampling(LATEST)");
        compileRule("sat_conn", null, suffix,
            "sw_stl_grpc_server_connection_count.downsampling(LATEST)");
    }

    // ==================== instance scope ====================

    @Test
    void instanceScopeWithNullLayerKey() throws Exception {
        final String suffix = "instance([\"service_subset\", \"service_name\", \"k8s_namespace\", \"service_cluster\", \"service_env\"], \"|\", [\"instance_name\"], \"|\", Layer.GENERAL, null)";
        for (final String metric : new String[]{
                "write_bytes", "write_count", "write_execute_time",
                "read_bytes", "read_count", "read_execute_time",
                "connect_connection_count", "connect_connection_time",
                "accept_connection_count", "accept_connection_time",
                "close_connection_count", "close_connection_time"}) {
            compileRule("inst_tcp_" + metric, null, suffix,
                "api_agent_tcp_" + metric + ".downsampling(SUM_PER_MIN)");
        }
    }

    // ==================== serviceRelation scope ====================

    @Test
    void serviceRelation6ArgWithForEachServer() throws Exception {
        compileRule("rel_api_srv_cpm", FOREACH_COMPONENT_PREFIX, RELATION_SERVER_SUFFIX,
            "metric_server_api_total_count.downsampling(SUM_PER_MIN)");
        compileRule("rel_api_srv_dur", FOREACH_COMPONENT_PREFIX, RELATION_SERVER_SUFFIX,
            "metric_server_api_total_duration.downsampling(SUM_PER_MIN)");
        compileRule("rel_api_srv_suc", FOREACH_COMPONENT_PREFIX, RELATION_SERVER_SUFFIX,
            "metric_server_api_http_success_count.downsampling(SUM_PER_MIN)");
        compileRule("rel_api_srv_pct", FOREACH_COMPONENT_PREFIX, RELATION_SERVER_SUFFIX,
            "metric_server_api_response_time_histogram" + HISTOGRAM_12_LABELS);
    }

    @Test
    void serviceRelation6ArgWithForEachClient() throws Exception {
        compileRule("rel_api_cli_cpm", FOREACH_COMPONENT_PREFIX, RELATION_CLIENT_SUFFIX,
            "metric_client_api_total_count.downsampling(SUM_PER_MIN)");
        compileRule("rel_api_cli_dur", FOREACH_COMPONENT_PREFIX, RELATION_CLIENT_SUFFIX,
            "metric_client_api_total_duration.downsampling(SUM_PER_MIN)");
        compileRule("rel_api_cli_suc", FOREACH_COMPONENT_PREFIX, RELATION_CLIENT_SUFFIX,
            "metric_client_api_http_success_count.downsampling(SUM_PER_MIN)");
        compileRule("rel_api_cli_pct", FOREACH_COMPONENT_PREFIX, RELATION_CLIENT_SUFFIX,
            "metric_client_api_response_time_histogram" + HISTOGRAM_12_LABELS);
    }

    @Test
    void serviceRelationTcpWithForEachServer() throws Exception {
        for (final String metric : new String[]{
                "write_bytes", "write_count", "write_execute_time",
                "read_bytes", "read_count", "read_execute_time",
                "connect_connection_count", "connect_connection_time",
                "accept_connection_count", "accept_connection_time",
                "close_connection_count", "close_connection_time"}) {
            compileRule("rel_tcp_srv_" + metric, FOREACH_COMPONENT_PREFIX, RELATION_SERVER_SUFFIX,
                "metric_server_tcp_" + metric + ".downsampling(SUM_PER_MIN)");
        }
    }

    @Test
    void serviceRelationTcpWithForEachClient() throws Exception {
        for (final String metric : new String[]{
                "write_bytes", "write_count", "write_execute_time",
                "read_bytes", "read_count", "read_execute_time",
                "connect_connection_count", "connect_connection_time",
                "accept_connection_count", "accept_connection_time",
                "close_connection_count", "close_connection_time"}) {
            compileRule("rel_tcp_cli_" + metric, FOREACH_COMPONENT_PREFIX, RELATION_CLIENT_SUFFIX,
                "metric_client_tcp_" + metric + ".downsampling(SUM_PER_MIN)");
        }
    }
}
