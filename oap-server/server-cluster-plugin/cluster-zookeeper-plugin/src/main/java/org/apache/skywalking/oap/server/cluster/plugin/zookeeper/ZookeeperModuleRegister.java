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
import org.apache.curator.x.discovery.*;
import org.apache.skywalking.oap.server.core.cluster.*;

/**
 * @author peng-yongsheng
 */
public class ZookeeperModuleRegister implements ModuleRegister {

    private final ServiceDiscovery<InstanceDetails> serviceDiscovery;
    private final ServiceCacheManager cacheManager;

    ZookeeperModuleRegister(ServiceDiscovery<InstanceDetails> serviceDiscovery,
        ServiceCacheManager cacheManager) {
        this.serviceDiscovery = serviceDiscovery;
        this.cacheManager = cacheManager;
    }

    @Override public void register(String moduleName, String providerName,
        InstanceDetails instanceDetails) throws ServiceRegisterException {
        try {
            String name = NodeNameBuilder.build(moduleName, providerName);

            ServiceInstance<InstanceDetails> thisInstance = ServiceInstance.<InstanceDetails>builder()
                .name(NodeNameBuilder.build(moduleName, providerName))
                .id(UUID.randomUUID().toString())
                .address(instanceDetails.getHost())
                .port(instanceDetails.getPort())
//                .uriSpec(new UriSpec(StringUtils.isEmpty(instanceDetails.getContextPath()) ? StringUtils.EMPTY_STRING : instanceDetails.getContextPath()))
                .payload(instanceDetails)
                .build();

            serviceDiscovery.registerService(thisInstance);

            ServiceCache<InstanceDetails> serviceCache = serviceDiscovery.serviceCacheBuilder()
                .name(name)
                .build();
            serviceCache.start();

            cacheManager.put(name, serviceCache);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServiceRegisterException(e.getMessage());
        }
    }
}
