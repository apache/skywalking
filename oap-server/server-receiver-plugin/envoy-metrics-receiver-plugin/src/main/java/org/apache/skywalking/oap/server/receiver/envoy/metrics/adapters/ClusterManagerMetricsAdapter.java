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

package org.apache.skywalking.oap.server.receiver.envoy.metrics.adapters;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.prometheus.client.Metrics;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;

@Slf4j
@RequiredArgsConstructor
public class ClusterManagerMetricsAdapter {
    private static final String DEFAULT_VALUE = "-";
    private final EnvoyMetricReceiverConfig config;

    public String adaptMetricsName(final Metrics.MetricFamily metricFamily) {

        return "envoy_cluster_metrics";
    }

    public Map<String, String> adaptLabels(final Metrics.MetricFamily metricFamily, final Map<String, String> labels) {
        String metricsName = metricFamily.getName();
        labels.putIfAbsent("metrics_name", metricsName);

        String clusterName = null;

        try {
            clusterName = buildUpstreamServiceMetaInfo(metricFamily).getServiceName();
        } catch (Exception e) {
            log.error("Failed to build upstream serviceMetaInfo from metrics name. ", e);
        }

        if (StringUtil.isNotEmpty(clusterName)) {
            labels.putIfAbsent("cluster_name", clusterName);
        }

        return labels;
    }

    protected ServiceMetaInfo buildUpstreamServiceMetaInfo(final Metrics.MetricFamily metricFamily) throws Exception {
        String metricsName = metricFamily.getName();
        String serviceName = DEFAULT_VALUE;
        String ns = DEFAULT_VALUE;
        String version = DEFAULT_VALUE;

        String[] splitArrGeneral = StringUtils.split(metricsName, ".");
        if (metricsName.startsWith("cluster.outbound")) {
            String[] splitArrBound = metricsName.split("\\|");
            if (splitArrBound.length > 3) {
                String[] splitArrClusterName = StringUtils.split(splitArrBound[3], ".");
                version = splitArrBound[2];
                if (splitArrClusterName.length > 1) {
                    if (StringUtil.isBlank(version)) {
                        version = "*";
                    }
                    serviceName = splitArrClusterName[0];
                    ns = splitArrClusterName[1];
                }
            }
        } else if (metricsName.startsWith("cluster.inbound")) {
            String[] splitArrBound = metricsName.split("\\|");
            if (splitArrBound.length > 1) {
                String[] splitArrClusterName = StringUtils.split(splitArrBound[0], ".");
                if (splitArrClusterName.length > 1) {
                    serviceName = splitArrClusterName[1] + ":" + splitArrBound[1];
                }
            }
        } else if (splitArrGeneral.length == 3) {
            serviceName = splitArrGeneral[1];
        }

        Value nsValue = Value.newBuilder().setStringValue(ns).build();
        Value nameValue = Value.newBuilder().setStringValue(serviceName).build();
        Value versionValue = Value.newBuilder().setStringValue(version).build();
        Struct labelStruct = Struct.newBuilder()
                                   .putFields("service.istio.io/canonical-name", nameValue)
                                   .putFields("app.kubernetes.io/name", nameValue)
                                   .putFields("app", nameValue)
                                   .putFields("service.istio.io/canonical-revision", versionValue)
                                   .putFields("version", versionValue).build();
        Value label = Value.newBuilder().setStructValue(labelStruct).build();
        Struct struct = Struct.newBuilder().putFields("NAMESPACE", nsValue).putFields("LABELS", label).build();
        return config.serviceMetaInfoFactory().fromStruct(struct);
    }
}
