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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.metrics.adapters.ClusterManagerMetricsAdapter;

public class EnvoyMetricReceiverConfig extends ModuleConfig {
    @Getter
    private boolean acceptMetricsService = false;
    private String alsHTTPAnalysis;
    private String alsTCPAnalysis;
    @Getter
    private String k8sServiceNameRule;
    @Getter
    private String istioServiceNameRule;
    private String istioServiceEntryIgnoredNamespaces;

    @Getter
    private String gRPCHost;
    @Getter
    private int gRPCPort;
    @Getter
    private int maxConcurrentCallsPerConnection;
    @Getter
    private int maxMessageSize;
    @Getter
    private int gRPCThreadPoolSize;
    @Getter
    private boolean gRPCSslEnabled = false;
    @Getter
    private String gRPCSslKeyPath;
    @Getter
    private String gRPCSslCertChainPath;
    @Getter
    private String gRPCSslTrustedCAsPath;

    private final ServiceMetaInfoFactory serviceMetaInfoFactory = new ServiceMetaInfoFactoryImpl();
    @Getter
    private final ClusterManagerMetricsAdapter clusterManagerMetricsAdapter = new ClusterManagerMetricsAdapter(this);

    public List<String> getAlsHTTPAnalysis() {
        if (Strings.isNullOrEmpty(alsHTTPAnalysis)) {
            return Collections.emptyList();
        }
        return Arrays.stream(alsHTTPAnalysis.trim().split(",")).map(String::trim).collect(Collectors.toList());
    }

    public List<String> getAlsTCPAnalysis() {
        if (Strings.isNullOrEmpty(alsTCPAnalysis)) {
            return Collections.emptyList();
        }
        return Arrays.stream(alsTCPAnalysis.trim().split(",")).map(String::trim).collect(Collectors.toList());
    }

    public List<Rule> rules() throws ModuleStartException {
        try {
            return Rules.loadRules("envoy-metrics-rules", Arrays.asList("envoy", "envoy-svc-relation"));
        } catch (IOException e) {
            throw new ModuleStartException("Failed to load envoy-metrics-rules", e);
        }
    }

    public ServiceMetaInfoFactory serviceMetaInfoFactory() {
        return serviceMetaInfoFactory;
    }

    public Set<String> getIstioServiceEntryIgnoredNamespaces() {
        final var s = Strings.nullToEmpty(istioServiceEntryIgnoredNamespaces);
        return Splitter.on(",").omitEmptyStrings().trimResults().splitToStream(s).collect(Collectors.toSet());
    }
}
