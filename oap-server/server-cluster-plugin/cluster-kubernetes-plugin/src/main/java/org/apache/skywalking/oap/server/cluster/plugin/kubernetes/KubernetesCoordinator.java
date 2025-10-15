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

package org.apache.skywalking.oap.server.cluster.plugin.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceQueryException;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

import com.google.common.base.Strings;
import com.linecorp.armeria.client.endpoint.EndpointGroup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Read collector pod info from Kubernetes using KubernetesLabelSelectorEndpointGroup, then construct the list of
 * {@link RemoteInstance}.
 */
@Slf4j
public class KubernetesCoordinator extends ClusterCoordinator {
    private final ModuleDefineHolder manager;
    private volatile int port = -1;
    private HealthCheckMetrics healthChecker;
    private ClusterModuleKubernetesConfig config;

    private EndpointGroup endpointGroup;
    private volatile List<RemoteInstance> remoteInstances;

    public KubernetesCoordinator(final ModuleDefineHolder manager,
                                 final ClusterModuleKubernetesConfig config) {
        this.manager = manager;
        this.config = config;

        if (Strings.isNullOrEmpty(config.getLabelSelector())) {
            throw new IllegalArgumentException("kubernetes labelSelector must be provided");
        }
    }

    private EndpointGroup createEndpointGroup() {
        if (port == -1) {
            port = manager.find(CoreModule.NAME).provider().getService(ConfigService.class).getGRPCPort();
        }
        final var kubernetesClient = new KubernetesClientBuilder().build();
        final var builder = KubernetesLabelSelectorEndpointGroup.builder(kubernetesClient);

        if (StringUtil.isNotBlank(config.getNamespace())) {
            builder.namespace(config.getNamespace());
        }

        final var labelMap = parseLabelSelector(config.getLabelSelector());
        builder.labelSelector(labelMap);

        builder.port(port);
        builder.selfUid(new UidEnvSupplier(config.getUidEnvName()).get());

        return builder.build();
    }

    private Map<String, String> parseLabelSelector(String labelSelector) {
        final var labels = new HashMap<String, String>();
        if (StringUtil.isBlank(labelSelector)) {
            return labels;
        }

        final var pairs = labelSelector.split(",");
        for (final var pair : pairs) {
            final var keyValue = pair.trim().split("=", 2);
            if (keyValue.length == 2) {
                labels.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return labels;
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        try {
            healthChecker.health();
            return remoteInstances;
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new ServiceQueryException(e.getMessage());
        }
    }

    @Override
    public void registerRemote(final RemoteInstance remoteInstance) throws ServiceRegisterException {
        try {
            this.port = remoteInstance.getAddress().getPort();
            healthChecker.health();
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new ServiceRegisterException(e.getMessage());
        }
    }

    private void initHealthChecker() {
        if (healthChecker == null) {
            MetricsCreator metricCreator = manager.find(TelemetryModule.NAME)
                                                  .provider()
                                                  .getService(MetricsCreator.class);
            healthChecker = metricCreator.createHealthCheckerGauge(
                "cluster_k8s", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        }
    }

    @Override
    public void start() {
        endpointGroup = createEndpointGroup();
        endpointGroup.addListener(endpoints -> {
            if (port == -1) {
                port = manager.find(CoreModule.NAME).provider().getService(ConfigService.class).getGRPCPort();
            }

            if (log.isDebugEnabled()) {
                log.debug("[kubernetes cluster endpoints]: {}", endpoints);
            }

            final var instances = endpoints.stream()
                    .map(endpoint -> new RemoteInstance(new Address(endpoint.host(), endpoint.port(), false)))
                    .collect(Collectors.toList());

            // The endpoint group will never include itself, add it.
            final var selfInstance = new RemoteInstance(new Address("127.0.0.1", port, true));
            instances.add(selfInstance);

            if (log.isDebugEnabled()) {
                instances.forEach(instance -> log.debug("kubernetes cluster instance: {}", instance));
            }

            this.remoteInstances = instances;
            notifyWatchers(instances);
        }, true);
        initHealthChecker();
    }
}
