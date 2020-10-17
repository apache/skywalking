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

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceQueryException;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.HealthCheckUtil;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;

public class NacosCoordinator implements ClusterRegister, ClusterNodesQuery {

    private final NamingService namingService;
    private final ClusterModuleNacosConfig config;
    private volatile Address selfAddress;
    @Setter
    private HealthCheckMetrics healthChecker;

    public NacosCoordinator(NamingService namingService, ClusterModuleNacosConfig config) {
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
            if (remoteInstances.size() > 1) {
                Set<String> remoteAddressSet = remoteInstances.stream().map(remoteInstance ->
                        remoteInstance.getAddress().getHost()).collect(Collectors.toSet());
                boolean hasUnHealthAddress = HealthCheckUtil.hasUnHealthAddress(remoteAddressSet);
                if (hasUnHealthAddress) {
                    this.healthChecker.unHealth(new ServiceQueryException("found 127.0.0.1 or localhost in cluster mode"));
                } else {
                    List<RemoteInstance> selfInstances = remoteInstances.stream().
                            filter(remoteInstance -> remoteInstance.getAddress().isSelf()).collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(selfInstances) && selfInstances.size() == 1) {
                        this.healthChecker.health();
                    } else {
                        this.healthChecker.unHealth(new ServiceQueryException("can't get self instance or multi self instances"));
                    }
                }
            }
        } catch (Throwable e) {
            healthChecker.unHealth(e);
            throw new ServiceQueryException(e.getMessage());
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
}
