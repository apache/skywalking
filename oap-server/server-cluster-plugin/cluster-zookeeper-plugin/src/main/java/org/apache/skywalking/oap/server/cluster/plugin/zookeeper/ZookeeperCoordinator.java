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

package org.apache.skywalking.oap.server.cluster.plugin.zookeeper;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.x.discovery.ServiceCache;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.ServiceCacheListener;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.core.cluster.ClusterHealthStatus;
import org.apache.skywalking.oap.server.core.cluster.OAPNodeChecker;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceQueryException;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class ZookeeperCoordinator extends ClusterCoordinator {

    private static final String REMOTE_NAME_PATH = "remote";

    private final ModuleDefineHolder manager;
    private final ClusterModuleZookeeperConfig config;
    private final ServiceDiscovery<RemoteInstance> serviceDiscovery;
    private final ServiceCache<RemoteInstance> serviceCache;
    private volatile Address selfAddress;
    private HealthCheckMetrics healthChecker;

    ZookeeperCoordinator(final ModuleDefineHolder manager, final ClusterModuleZookeeperConfig config,
                         final ServiceDiscovery<RemoteInstance> serviceDiscovery) throws Exception {
        this.manager = manager;
        this.config = config;
        this.serviceDiscovery = serviceDiscovery;
        this.serviceCache = serviceDiscovery.serviceCacheBuilder().name(REMOTE_NAME_PATH).build();
        this.serviceCache.start();
    }

    @Override
    public synchronized void registerRemote(RemoteInstance remoteInstance) throws ServiceRegisterException {
        try {
            initHealthChecker();
            if (needUsingInternalAddr()) {
                remoteInstance = new RemoteInstance(new Address(config.getInternalComHost(), config.getInternalComPort(), true));
            }

            ServiceInstance<RemoteInstance> thisInstance = ServiceInstance.<RemoteInstance>builder().name(REMOTE_NAME_PATH)
                                                                                                    .id(UUID.randomUUID()
                                                                                                            .toString())
                                                                                                    .address(remoteInstance
                                                                                                                 .getAddress()
                                                                                                                 .getHost())
                                                                                                    .port(remoteInstance
                                                                                                              .getAddress()
                                                                                                              .getPort())
                                                                                                    .payload(remoteInstance)
                                                                                                    .build();

            serviceDiscovery.registerService(thisInstance);

            this.selfAddress = remoteInstance.getAddress();
            this.healthChecker.health();
        } catch (Throwable e) {
            this.healthChecker.unHealth(e);
            throw new ServiceRegisterException(e.getMessage());
        }
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        List<RemoteInstance> remoteInstances = new ArrayList<>(20);
        try {
            initHealthChecker();
            List<ServiceInstance<RemoteInstance>> serviceInstances = serviceCache.getInstances();
            serviceInstances.forEach(serviceInstance -> {
                RemoteInstance instance = serviceInstance.getPayload();
                if (instance.getAddress().equals(selfAddress)) {
                    instance.getAddress().setSelf(true);
                } else {
                    instance.getAddress().setSelf(false);
                }
                remoteInstances.add(instance);
            });
            ClusterHealthStatus healthStatus = OAPNodeChecker.isHealth(remoteInstances);
            if (healthStatus.isHealth()) {
                this.healthChecker.health();
            } else {
                this.healthChecker.unHealth(healthStatus.getReason());
            }
        } catch (Throwable e) {
            this.healthChecker.unHealth(e);
            throw new ServiceQueryException(e.getMessage());
        }

        if (log.isDebugEnabled()) {
            remoteInstances.forEach(instance -> log.debug("Zookeeper cluster instance: {}", instance.toString()));
        }
        return remoteInstances;
    }

    private boolean needUsingInternalAddr() {
        return !Strings.isNullOrEmpty(config.getInternalComHost()) && config.getInternalComPort() > 0;
    }

    private void initHealthChecker() {
        if (healthChecker == null) {
            MetricsCreator metricCreator = manager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
            healthChecker = metricCreator.createHealthCheckerGauge("cluster_zookeeper", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        }
    }

    @Override
    protected void start() {
        initHealthChecker();
        serviceCache.addListener(new ZookeeperEventListener());
    }

    class ZookeeperEventListener implements ServiceCacheListener {
        @Override
        public void cacheChanged() {
            try {
                List<RemoteInstance> remoteInstances = queryRemoteNodes();
                notifyWatchers(remoteInstances);
            } catch (Throwable e) {
                healthChecker.unHealth(e);
                log.error("Failed to notify and update remote instances", e);
            }
        }

        @Override
        public void stateChanged(final CuratorFramework client, final ConnectionState newState) {
            if (log.isDebugEnabled()) {
                log.debug("Zookeeper ConnectionState changed, state: {}", newState.name());
            }
        }
    }
}
