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

package org.apache.skywalking.oap.server.receiver.envoy;

import io.prometheus.client.Metrics;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.library.util.FieldsHelper;
import org.apache.skywalking.oap.server.receiver.envoy.metrics.adapters.ClusterManagerMetricsAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import java.util.HashMap;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ClusterManagerMetricsAdapterTest {

    private ClusterManagerMetricsAdapter clusterManagerMetricsAdapter;
    private Metrics.MetricFamily generalName = Metrics.MetricFamily.newBuilder().setName("cluster.sds-grpc.upstream_cx_total").build();
    private Metrics.MetricFamily cbNameOutboundFQDN = Metrics.MetricFamily.newBuilder().setName("cluster.outbound|9080||reviews.default.svc.cluster.local.circuit_breakers.default.cx_pool_open").build();
    private Metrics.MetricFamily cbNameOutboundFQDNSubset = Metrics.MetricFamily.newBuilder().setName("cluster.outbound|9080|v1|reviews.default.svc.cluster.local.circuit_breakers.default.cx_pool_open").build();
    private Metrics.MetricFamily cbNameInboundFQDN = Metrics.MetricFamily.newBuilder().setName("cluster.inbound|9080||.upstream_cx_total").build();

    @SneakyThrows
    @BeforeEach
    public void setUp() {
        Whitebox.setInternalState(FieldsHelper.forClass(this.getClass()), "initialized", false);
        EnvoyMetricReceiverConfig config = new EnvoyMetricReceiverConfig();
        clusterManagerMetricsAdapter = new ClusterManagerMetricsAdapter(config);
        FieldsHelper.forClass(config.serviceMetaInfoFactory().clazz()).init("metadata-service-mapping.yaml");
    }

    @Test
    public void testAdaptMetricsName() {

        assertThat(clusterManagerMetricsAdapter.adaptMetricsName(generalName)).isEqualTo("envoy_cluster_metrics");
    }

    @Test
    public void testAdaptLabels() {

        assertThat(
                clusterManagerMetricsAdapter.adaptLabels(generalName, new HashMap<>()).toString()
        ).isEqualTo("{cluster_name=-.sds-grpc.-, metrics_name=" + generalName.getName() + "}");
        assertThat(
                clusterManagerMetricsAdapter.adaptLabels(cbNameOutboundFQDN, new HashMap<>()).toString()
        ).isEqualTo("{cluster_name=*.reviews.default, metrics_name=" + cbNameOutboundFQDN.getName() + "}");
        assertThat(
                clusterManagerMetricsAdapter.adaptLabels(cbNameOutboundFQDNSubset, new HashMap<>()).toString()
        ).isEqualTo("{cluster_name=v1.reviews.default, metrics_name=" + cbNameOutboundFQDNSubset.getName() + "}");
        assertThat(
                clusterManagerMetricsAdapter.adaptLabels(cbNameInboundFQDN, new HashMap<>()).toString()
        ).isEqualTo("{cluster_name=-.inbound:9080.-, metrics_name=" + cbNameInboundFQDN.getName() + "}");

    }
}
