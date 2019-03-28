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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.google.common.base.Strings;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.telemetry.api.TelemetryRelatedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author litian33@gmail.com
 */
public class NacosCoordinator implements ClusterRegister, ClusterNodesQuery {
    private static final Logger logger = LoggerFactory.getLogger(NacosCoordinator.class);

    private final NamingService client;
    private final String serviceName;
    private volatile Address selfAddress;

    public NacosCoordinator(ClusterModuleNacosConfig config, NamingService client) {
        this.client = client;
        this.serviceName = config.getServiceName();
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        List<Instance> nodes;
        try {
            nodes = client.selectInstances(serviceName, true);
        } catch (NacosException e) {
            logger.error("query service instance error", e);
            return remoteInstances;
        }

        if (CollectionUtils.isNotEmpty(nodes)) {
            nodes.forEach(node -> {
                if (!Strings.isNullOrEmpty(node.getIp())) {
                    if (Objects.nonNull(selfAddress)) {
                        if (selfAddress.getHost().equals(node.getIp()) && selfAddress.getPort() == node.getPort()) {
                            remoteInstances.add(new RemoteInstance(new Address(node.getIp(), node.getPort(), true)));
                        } else {
                            remoteInstances.add(new RemoteInstance(new Address(node.getIp(), node.getPort(), false)));
                        }
                    }
                }
            });
        }
        return remoteInstances;
    }

    @Override
    public void registerRemote(RemoteInstance remoteInstance) throws ServiceRegisterException {
        this.selfAddress = remoteInstance.getAddress();
        TelemetryRelatedContext.INSTANCE.setId(selfAddress.toString());

        try {
            client.registerInstance(serviceName, remoteInstance.getAddress().getHost(), remoteInstance.getAddress().getPort());
        } catch (NacosException e) {
            logger.error("register local instance as service error", e);
            throw new ServiceRegisterException(e.getMessage());
        }
    }
}
