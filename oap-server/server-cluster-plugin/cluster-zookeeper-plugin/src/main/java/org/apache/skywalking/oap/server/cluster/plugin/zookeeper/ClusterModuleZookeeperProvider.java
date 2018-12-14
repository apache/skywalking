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

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.*;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.*;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.cluster.*;
import org.apache.skywalking.oap.server.library.module.*;
import org.slf4j.*;

/**
 * Use Zookeeper to manage all instances in SkyWalking cluster.
 *
 * @author peng-yongsheng, Wu Sheng
 */
public class ClusterModuleZookeeperProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(ClusterModuleZookeeperProvider.class);

    private static final String BASE_PATH = "/skywalking";

    private final ClusterModuleZookeeperConfig config;
    private CuratorFramework client;
    private ServiceDiscovery<RemoteInstance> serviceDiscovery;

    public ClusterModuleZookeeperProvider() {
        super();
        this.config = new ClusterModuleZookeeperConfig();
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

    @Override public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(config.getBaseSleepTimeMs(), config.getMaxRetries());
        client = CuratorFrameworkFactory.newClient(config.getHostPort(), retryPolicy);

        String path = BASE_PATH + (StringUtil.isEmpty(config.getNameSpace()) ? "" : "/" + config.getNameSpace());

        serviceDiscovery = ServiceDiscoveryBuilder.builder(RemoteInstance.class).client(client)
            .basePath(path)
            .watchInstances(true)
            .serializer(new SWInstanceSerializer()).build();

        try {
            client.start();
            client.blockUntilConnected();
            serviceDiscovery.start();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ModuleStartException(e.getMessage(), e);
        }

        ZookeeperCoordinator coordinator = new ZookeeperCoordinator(serviceDiscovery);
        this.registerServiceImplementation(ClusterRegister.class, coordinator);
        this.registerServiceImplementation(ClusterNodesQuery.class, coordinator);
    }

    @Override public void start() {
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[0];
    }
}
