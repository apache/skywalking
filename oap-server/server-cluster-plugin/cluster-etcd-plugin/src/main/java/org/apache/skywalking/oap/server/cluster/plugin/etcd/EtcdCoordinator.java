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
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.telemetry.api.TelemetryRelatedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alan Lau
 */
public class EtcdCoordinator implements ClusterRegister, ClusterNodesQuery {
    private static final Logger logger = LoggerFactory.getLogger(EtcdCoordinator.class);

    private ClusterModuleEtcdConfig config;

    private EtcdClient client;

    private volatile Address selfAddress;

    private final String serviceName;

    private ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();

    private static final Integer KEY_TTL = 45;

    public EtcdCoordinator(ClusterModuleEtcdConfig config, EtcdClient client) {
        this.config = config;
        this.client = client;
        this.serviceName = config.getServiceName();
    }

    @Override public List<RemoteInstance> queryRemoteNodes() {

        List<RemoteInstance> res = new ArrayList<>();
        try {
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
                    res.add(new RemoteInstance(address));
                });
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return res;
    }

    @Override public void registerRemote(RemoteInstance remoteInstance) throws ServiceRegisterException {

        if (needUsingInternalAddr()) {
            remoteInstance = new RemoteInstance(new Address(config.getInternalComHost(), config.getInternalComPort(), true));
        }

        this.selfAddress = remoteInstance.getAddress();
        TelemetryRelatedContext.INSTANCE.setId(selfAddress.toString());

        EtcdEndpoint endpoint = new EtcdEndpoint.Builder().serviceName(serviceName).host(selfAddress.getHost()).port(selfAddress.getPort()).build();
        try {
            client.putDir(serviceName).send();
            String key = buildKey(serviceName, selfAddress, remoteInstance);
            String json = new Gson().toJson(endpoint);
            EtcdResponsePromise<EtcdKeysResponse> promise = client.put(key, json).ttl(KEY_TTL).send();
            //check register.
            promise.get();
            renew(client, key, json);
        } catch (Exception e) {
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
                    logger.error(ee.getMessage(), ee);
                }
            }
        }, 5 * 1000, 30 * 1000, TimeUnit.MILLISECONDS);
    }

    private String buildKey(String serviceName, Address address, RemoteInstance instance) {
        return new StringBuilder(serviceName).append("/").append(address.getHost()).append("_").append(instance.hashCode()).toString();
    }

    private boolean needUsingInternalAddr() {
        return !Strings.isNullOrEmpty(config.getInternalComHost()) && config.getInternalComPort() > 0;
    }
}
