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
import org.apache.skywalking.oap.server.receiver.envoy.metrics.adapters.ListenerMetricsAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ListenerMetricsAdapterTest {

    private ListenerMetricsAdapter listenerMetricsAdapter;
    private final Metrics.MetricFamily downstream = Metrics.MetricFamily.newBuilder()
                                                                        .setName(
                                                                            "listener.0.0.0.0_15090.downstream_cx_total")
                                                                        .build();
    private final Metrics.MetricFamily http = Metrics.MetricFamily.newBuilder()
                                                                  .setName(
                                                                      "listener.0.0.0.0_15090.http.inbound_0.0.0.0_15090.downstream_rq_2xx")
                                                                  .build();
    private final Metrics.MetricFamily adminListener = Metrics.MetricFamily.newBuilder()
                                                                           .setName(
                                                                               "listener.admin.downstream_cx_destroy")
                                                                           .build();
    private final Metrics.MetricFamily virtualListener = Metrics.MetricFamily.newBuilder()
                                                                             .setName(
                                                                                 "listener.0.0.0.0_15001.downstream_cx_active")
                                                                             .build();
    private final Metrics.MetricFamily ca = Metrics.MetricFamily.newBuilder()
                                                                .setName(
                                                                    "listener.0.0.0.0_15006.ssl.certificate.ROOTCA.expiration_unix_time_seconds")
                                                                .build();

    @BeforeEach
    public void setUp() {
        listenerMetricsAdapter = new ListenerMetricsAdapter();
    }

    @Test
    public void testAdaptMetricsName() {
        assertThat(listenerMetricsAdapter.adaptMetricsName(downstream)).isEqualTo("envoy_listener_metrics");
        assertThat(listenerMetricsAdapter.adaptMetricsName(http)).isEqualTo("envoy_listener_metrics");
        assertThat(listenerMetricsAdapter.adaptMetricsName(adminListener)).isEqualTo("envoy_listener_metrics");
        assertThat(listenerMetricsAdapter.adaptMetricsName(virtualListener)).isEqualTo("envoy_listener_metrics");
        assertThat(listenerMetricsAdapter.adaptMetricsName(ca)).isEqualTo("envoy_listener_metrics");
    }

    @Test
    public void testAdaptLabels() {
        assertThat(
            listenerMetricsAdapter.adaptLabels(downstream, new HashMap<>()).toString()
        ).isEqualTo("{metrics_name=" + downstream.getName() + "}");

        assertThat(
            listenerMetricsAdapter.adaptLabels(http, new HashMap<>()).toString()
        ).isEqualTo("{metrics_name=" + http.getName() + "}");

        assertThat(
            listenerMetricsAdapter.adaptLabels(adminListener, new HashMap<>()).toString()
        ).isEqualTo("{metrics_name=" + adminListener.getName() + "}");

        assertThat(
            listenerMetricsAdapter.adaptLabels(virtualListener, new HashMap<>()).toString()
        ).isEqualTo("{metrics_name=" + virtualListener.getName() + "}");
        assertThat(
            listenerMetricsAdapter.adaptLabels(ca, new HashMap<>()).toString()
        ).isEqualTo("{metrics_name=" + ca.getName() + "}");
    }
}
