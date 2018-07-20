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

import java.util.UUID;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ZookeeperNodeRegister implements ClusterRegister {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperNodeRegister.class);

    private final ServiceDiscovery<RemoteInstance> serviceDiscovery;
    private final String nodeName;

    ZookeeperNodeRegister(ServiceDiscovery<RemoteInstance> serviceDiscovery, String nodeName) {
        this.serviceDiscovery = serviceDiscovery;
        this.nodeName = nodeName;
    }

    @Override public synchronized void registerRemote(RemoteInstance remoteInstance) throws ServiceRegisterException {
        try {
            ServiceInstance<RemoteInstance> thisInstance = ServiceInstance.<RemoteInstance>builder()
                .name(nodeName)
                .id(UUID.randomUUID().toString())
                .address(remoteInstance.getHost())
                .port(remoteInstance.getPort())
                .payload(remoteInstance)
                .build();

            serviceDiscovery.registerService(thisInstance);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ServiceRegisterException(e.getMessage());
        }
    }
}
