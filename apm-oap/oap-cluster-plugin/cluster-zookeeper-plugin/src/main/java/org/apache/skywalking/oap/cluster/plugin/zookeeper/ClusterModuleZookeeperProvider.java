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

package org.apache.skywalking.oap.cluster.plugin.zookeeper;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.*;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.*;
import org.apache.skywalking.oap.core.cluster.*;
import org.apache.skywalking.oap.library.module.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ClusterModuleZookeeperProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(ClusterModuleZookeeperProvider.class);

    private static final String BASE_PATH = "/skywalking";

    private final ServiceCacheManager cacheManager;
    private final ClusterModuleZookeeperConfig config;
    private CuratorFramework client;
    private ServiceDiscovery<InstanceDetails> serviceDiscovery;

    public ClusterModuleZookeeperProvider() {
        super();
        this.config = new ClusterModuleZookeeperConfig();
        this.cacheManager = new ServiceCacheManager();
    }

    @Override public String name() {
        return "zookeeper";
    }

    @Override public Class module() {
        return ClusterModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(config.getBaseSleepTimeMs(), config.getMaxRetries());
        client = CuratorFrameworkFactory.newClient(config.getHostPort(), retryPolicy);

        serviceDiscovery = ServiceDiscoveryBuilder.builder(InstanceDetails.class).client(client)
            .basePath(BASE_PATH)
            .watchInstances(true)
            .serializer(new SWInstanceSerializer()).build();

        this.registerServiceImplementation(ServiceRegister.class, new ZookeeperServiceRegister(serviceDiscovery, cacheManager));
        this.registerServiceImplementation(ServiceQuery.class, new ZookeeperServiceQuery(cacheManager));
    }

    @Override public void start() throws ModuleStartException {
        try {
            client.start();
            client.blockUntilConnected();
            serviceDiscovery.start();
        } catch (Exception e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override public void notifyAfterCompleted() {
    }
}
