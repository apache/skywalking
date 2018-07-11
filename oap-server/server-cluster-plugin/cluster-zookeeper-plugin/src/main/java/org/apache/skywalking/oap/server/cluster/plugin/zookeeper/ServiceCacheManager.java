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
import java.util.concurrent.ConcurrentHashMap;
import org.apache.curator.x.discovery.ServiceCache;
import org.apache.skywalking.oap.server.core.cluster.InstanceDetails;

/**
 * @author peng-yongsheng
 */
public class ServiceCacheManager {

    private final Map<String, ServiceCache<InstanceDetails>> serviceCacheMap;

    public ServiceCacheManager() {
        this.serviceCacheMap = new ConcurrentHashMap<>();
    }

    public void put(String name, ServiceCache<InstanceDetails> cache) {
        serviceCacheMap.put(name, cache);
    }

    public ServiceCache<InstanceDetails> get(String name) {
        return serviceCacheMap.get(name);
    }
}
