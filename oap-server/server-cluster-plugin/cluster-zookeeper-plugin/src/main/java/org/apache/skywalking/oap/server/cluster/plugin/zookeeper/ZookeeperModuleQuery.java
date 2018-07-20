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

import java.util.*;
import org.apache.curator.x.discovery.ServiceCache;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.skywalking.oap.server.core.cluster.*;

/**
 * @author peng-yongsheng, Wu Sheng
 */
public class ZookeeperModuleQuery implements ClusterNodesQuery {

    private final ServiceCache<RemoteInstance> serviceCache;

    ZookeeperModuleQuery(ServiceCache<RemoteInstance> serviceCache) {
        this.serviceCache = serviceCache;
    }

    @Override
    public List<RemoteInstance> queryRemoteNodes() throws ServiceRegisterException {
        List<ServiceInstance<RemoteInstance>> serviceInstances = serviceCache.getInstances();

        List<RemoteInstance> remoteInstanceDetails = new ArrayList<>(serviceInstances.size());
        serviceInstances.forEach(serviceInstance -> remoteInstanceDetails.add(serviceInstance.getPayload()));
        return remoteInstanceDetails;
    }
}
