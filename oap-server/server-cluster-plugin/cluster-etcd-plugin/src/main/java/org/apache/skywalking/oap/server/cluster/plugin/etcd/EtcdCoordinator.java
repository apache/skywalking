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

package org.apache.skywalking.oap.server.cluster.plugin.etcd;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.skywalking.oap.server.core.cluster.ClusterHealthStatus;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.OAPNodeChecker;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdCoordinator implements ClusterRegister, ClusterNodesQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdCoordinator.class);
    private static final Integer KEY_TTL = 45;

    private final ModuleDefineHolder manager;
    private final ClusterModuleEtcdConfig config;
    private final EtcdClient client;
    private final String serviceName;
    private final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    private volatile Address selfAddress;
    private HealthCheckMetrics healthChecker;

    public EtcdCoordinator(final ModuleDefineHolder manager, final ClusterModuleEtcdConfig config, final EtcdClient client) {
        this.manager = manager;
        this.config = config;
        this.client = client;
        this.serviceName = config.getServiceName();
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        try {
            initHealthChecker();
            EtcdKeysResponse response = client.get(serviceName + "/").send().get();
            List<EtcdKeysResponse.EtcdNode> nodes = response.getNode().getNodes();

            Gson gson = new Gson();
            if (nodes != null) {
                nodes.forEach(node -> {
                    EtcdEndpoint endpoint = gson.fromJson(node.getValue(), EtcdEndpoint.class);
                    Address address = new Address(endpoint.getHost(), endpoint.getPort(), true);
                    if (!address.equals(selfAddress)) {
                        address.setSelf(false);
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
            throw new RuntimeException(e);
        }
        return remoteInstances;
    }

    @Override
    public void registerRemote(RemoteInstance remoteInstance) throws ServiceRegisterException {

        if (needUsingInternalAddr()) {
            remoteInstance = new RemoteInstance(new Address(config.getInternalComHost(), config.getInternalComPort(), true));
        }

        this.selfAddress = remoteInstance.getAddress();

        EtcdEndpoint endpoint = new EtcdEndpoint.Builder().serviceName(serviceName)
                                                          .host(selfAddress.getHost())
                                                          .port(selfAddress.getPort())
                                                          .build();
        try {
            initHealthChecker();
            client.putDir(serviceName).send();
            String key = buildKey(serviceName, selfAddress, remoteInstance);
            String json = new Gson().toJson(endpoint);
            EtcdResponsePromise<EtcdKeysResponse> promise = client.put(key, json).ttl(KEY_TTL).send();
            //check register.
            promise.get();
            renew(client, key, json);
            healthChecker.health();
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new ServiceRegisterException(e.getMessage());
        }

    }

    private void renew(EtcdClient client, String key, String json) {
        service.scheduleAtFixedRate(() -> {
            try {
                client.refresh(key, KEY_TTL).send().get();
            } catch (Exception e) {
                try {
                    client.put(key, json).ttl(KEY_TTL).send().get();
                } catch (Exception ee) {
                    LOGGER.error(ee.getMessage(), ee);
                }
            }
        }, 5 * 1000, 30 * 1000, TimeUnit.MILLISECONDS);
    }

    private String buildKey(String serviceName, Address address, RemoteInstance instance) {
        return new StringBuilder(serviceName).append("/")
                                             .append(address.getHost())
                                             .append("_")
                                             .append(instance.hashCode())
                                             .toString();
    }

    private boolean needUsingInternalAddr() {
        return !Strings.isNullOrEmpty(config.getInternalComHost()) && config.getInternalComPort() > 0;
    }

    private void initHealthChecker() {
        if (healthChecker == null) {
            MetricsCreator metricCreator = manager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
            healthChecker = metricCreator.createHealthCheckerGauge("cluster_etcd", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        }
    }
}
