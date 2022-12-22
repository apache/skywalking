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

package org.apache.skywalking.oap.server.cluster.plugin.consul;

import com.google.common.base.Strings;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.cache.ConsulCache;
import com.orbitz.consul.cache.ServiceHealthCache;
import com.orbitz.consul.cache.ServiceHealthKey;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.option.QueryOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.core.cluster.ClusterHealthStatus;
import org.apache.skywalking.oap.server.core.cluster.OAPNodeChecker;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceQueryException;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class ConsulCoordinator extends ClusterCoordinator {

    private final ModuleDefineHolder manager;
    private final Consul client;
    private final String serviceName;
    private final ClusterModuleConsulConfig config;
    private volatile Address selfAddress;
    private HealthCheckMetrics healthChecker;

    public ConsulCoordinator(final ModuleDefineHolder manager,
                             final ClusterModuleConsulConfig config,
                             final Consul client) {
        this.manager = manager;
        this.config = config;
        this.client = client;
        this.serviceName = config.getServiceName();
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        try {
            HealthClient healthClient = client.healthClient();
            // Discover only "passing" nodes
            List<ServiceHealth> nodes = healthClient.getHealthyServiceInstances(serviceName).getResponse();
            if (CollectionUtils.isNotEmpty(nodes)) {
                nodes.forEach(node -> {
                    if (!Strings.isNullOrEmpty(node.getService().getAddress())) {
                        Address address = new Address(node.getService().getAddress(), node.getService().getPort(), false);
                        if (address.equals(selfAddress)) {
                            address.setSelf(true);
                        }
                        remoteInstances.add(new RemoteInstance(address));
                    }
                });
            }
            ClusterHealthStatus healthStatus = OAPNodeChecker.isHealth(remoteInstances);
            if (healthStatus.isHealth()) {
                this.healthChecker.health();
            } else {
                this.healthChecker.unHealth(healthStatus.getReason());
            }
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new ServiceQueryException(e.getMessage());
        }
        if (log.isDebugEnabled()) {
            remoteInstances.forEach(instance -> log.debug("Cosule cluster instance: {}", instance));
        }
        return remoteInstances;
    }

    @Override
    public void registerRemote(RemoteInstance remoteInstance) throws ServiceRegisterException {
        if (needUsingInternalAddr()) {
            remoteInstance = new RemoteInstance(
                new Address(config.getInternalComHost(), config.getInternalComPort(), true));
        }
        try {
            AgentClient agentClient = client.agentClient();

            this.selfAddress = remoteInstance.getAddress();

            Registration registration = ImmutableRegistration.builder()
                                                             .id(remoteInstance.getAddress().toString())
                                                             .name(serviceName)
                                                             .address(remoteInstance.getAddress().getHost())
                                                             .port(remoteInstance.getAddress().getPort())
                                                             .check(Registration.RegCheck.grpc(
                                                                 remoteInstance.getAddress()
                                                                               .getHost() + ":" + remoteInstance
                                                                     .getAddress()
                                                                     .getPort(),
                                                                 5
                                                             )) // registers with a TTL of 5 seconds
                                                             .build();

            agentClient.register(registration);
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
                "cluster_consul", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        }
    }

    private boolean needUsingInternalAddr() {
        return !Strings.isNullOrEmpty(config.getInternalComHost()) && config.getInternalComPort() > 0;
    }

    private RemoteInstance buildRemoteInstance(String host, int port) {
        Address address = new Address(host, port, false);
        if (address.equals(selfAddress)) {
            address.setSelf(true);
        }
        return new RemoteInstance(address);
    }

    private void checkHealth(List<RemoteInstance> remoteInstances) {
        ClusterHealthStatus healthStatus = OAPNodeChecker.isHealth(remoteInstances);
        if (healthStatus.isHealth()) {
            this.healthChecker.health();
        } else {
            this.healthChecker.unHealth(healthStatus.getReason());
        }
    }

    @Override
    protected void start() {
        initHealthChecker();
        ServiceHealthCache svHealth = ServiceHealthCache.newCache(client.healthClient(), serviceName, true,
                                                                  QueryOptions.BLANK, 5);
        svHealth.addListener(new ConsulEventListener());
        svHealth.start();
    }

    /**
     * Notice: If the consul version > v1.10.0, the `consul-client ConsulCache` will throw error response:
     * "com.orbitz.consul.ConsulException: Consul cluster has no elected leader" and fails to retrieve data.
     * This is a known issue but doesn't release yet, can refer to: https://github.com/rickfast/consul-client/pull/456
     */
    class ConsulEventListener implements ConsulCache.Listener<ServiceHealthKey, ServiceHealth> {
        @Override
        public void notify(final Map<ServiceHealthKey, ServiceHealth> newValues) {
            try {
            if (newValues.size() > 0) {
                List<RemoteInstance> remoteInstances = new ArrayList<>(newValues.size());
                newValues.values().forEach(serviceHealth -> {
                    if (StringUtil.isNotBlank(serviceHealth.getService().getAddress())) {
                        RemoteInstance remoteInstance = buildRemoteInstance(
                            serviceHealth.getService().getAddress(), serviceHealth.getService().getPort());
                        remoteInstances.add(remoteInstance);
                    }
                });
                checkHealth(remoteInstances);
                notifyWatchers(remoteInstances);
            }
            } catch (Throwable e) {
                healthChecker.unHealth(e);
                log.error("Failed to notify and update remote instances.", e);
            }
        }
    }
}
