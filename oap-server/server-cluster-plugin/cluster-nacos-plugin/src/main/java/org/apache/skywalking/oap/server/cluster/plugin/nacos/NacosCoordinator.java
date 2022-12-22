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

package org.apache.skywalking.oap.server.cluster.plugin.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.cluster.ClusterCoordinator;
import org.apache.skywalking.oap.server.core.cluster.ClusterHealthStatus;
import org.apache.skywalking.oap.server.core.cluster.OAPNodeChecker;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceQueryException;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class NacosCoordinator extends ClusterCoordinator {

    private final ModuleDefineHolder manager;
    private final NamingService namingService;
    private final ClusterModuleNacosConfig config;
    private volatile Address selfAddress;
    private HealthCheckMetrics healthChecker;

    public NacosCoordinator(final ModuleDefineHolder manager, final NamingService namingService, final ClusterModuleNacosConfig config) {
        this.manager = manager;
        this.namingService = namingService;
        this.config = config;
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        try {
            List<Instance> instances = namingService.selectInstances(config.getServiceName(), true);
            if (CollectionUtils.isNotEmpty(instances)) {
                instances.forEach(instance -> {
                    Address address = new Address(instance.getIp(), instance.getPort(), false);
                    if (address.equals(selfAddress)) {
                        address.setSelf(true);
                    }
                    remoteInstances.add(new RemoteInstance(address));
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
            log.debug("Nacos cluster instances:{}", remoteInstances.toString());
        }

        return remoteInstances;
    }

    @Override
    public void registerRemote(RemoteInstance remoteInstance) throws ServiceRegisterException {
        if (needUsingInternalAddr()) {
            remoteInstance = new RemoteInstance(new Address(config.getInternalComHost(), config.getInternalComPort(), true));
        }
        String host = remoteInstance.getAddress().getHost();
        int port = remoteInstance.getAddress().getPort();
        try {
            namingService.registerInstance(config.getServiceName(), host, port);
            healthChecker.health();
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new ServiceRegisterException(e.getMessage());
        }
        this.selfAddress = remoteInstance.getAddress();
    }

    private boolean needUsingInternalAddr() {
        return !Strings.isNullOrEmpty(config.getInternalComHost()) && config.getInternalComPort() > 0;
    }

    private void initHealthChecker() {
        if (healthChecker == null) {
            MetricsCreator metricCreator = manager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
            healthChecker = metricCreator.createHealthCheckerGauge("cluster_nacos", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        }
    }

    private RemoteInstance buildRemoteInstance(Instance instance) {
        Address address = new Address(instance.getIp(), instance.getPort(), false);
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
    protected void start() throws ModuleStartException {
        initHealthChecker();
        try {
            namingService.subscribe(config.getServiceName(), new NacosEventListener());
        } catch (NacosException e) {
            throw new ModuleStartException("Failed to start watching remote instances in Nacos coordinator.", e);
        }
    }

    class NacosEventListener implements EventListener {
        @Override
        public void onEvent(final Event event) {
            try {
                List<Instance> instances = ((NamingEvent) event).getInstances();
                List<RemoteInstance> remoteInstances = new ArrayList<>(instances.size());
                instances.forEach(instance -> {
                    RemoteInstance remoteInstance = buildRemoteInstance(instance);
                    if (instance.isHealthy() && instance.isEnabled()) {
                        remoteInstances.add(remoteInstance);
                    }
                });
                checkHealth(remoteInstances);
                notifyWatchers(remoteInstances);
            } catch (Throwable e) {
                healthChecker.unHealth(e);
                log.error("Failed to notify and update remote instances.", e);
            }
        }
    }
}
