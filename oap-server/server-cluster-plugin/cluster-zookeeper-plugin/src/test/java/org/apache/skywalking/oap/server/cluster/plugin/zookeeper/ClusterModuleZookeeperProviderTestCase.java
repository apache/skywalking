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

import java.io.IOException;
import java.util.List;
import org.apache.curator.test.TestingServer;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.cluster.ServiceRegisterException;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author peng-yongsheng
 */
public class ClusterModuleZookeeperProviderTestCase {

    private TestingServer server;

    @Before
    public void before() throws Exception {
        server = new TestingServer(12181, true);
        server.start();
    }

    @Test
    public void testStart() throws ServiceNotProvidedException, ModuleStartException, ServiceRegisterException, InterruptedException {
        ClusterModuleZookeeperProvider provider = new ClusterModuleZookeeperProvider();
        ClusterModuleZookeeperConfig moduleConfig = (ClusterModuleZookeeperConfig)provider.createConfigBeanIfAbsent();
        moduleConfig.setHostPort(server.getConnectString());
        moduleConfig.setBaseSleepTimeMs(3000);
        moduleConfig.setMaxRetries(4);

        provider.prepare();
        provider.start();

        ClusterRegister moduleRegister = provider.getService(ClusterRegister.class);
        ClusterNodesQuery clusterNodesQuery = provider.getService(ClusterNodesQuery.class);

        RemoteInstance remoteInstance = new RemoteInstance();
        remoteInstance.setHost("ProviderAHost");
        remoteInstance.setPort(1000);

        moduleRegister.registerRemote(remoteInstance);

        for (int i = 0; i < 20; i++) {
            List<RemoteInstance> detailsList = clusterNodesQuery.queryRemoteNodes();
            if (detailsList.size() == 0) {
                Thread.sleep(500);
                continue;
            }
            Assert.assertEquals(1, detailsList.size());
            Assert.assertEquals("ProviderAHost", detailsList.get(0).getHost());
            Assert.assertEquals(1000, detailsList.get(0).getPort());
        }

    }

    @After
    public void after() throws IOException {
        server.stop();
    }
}
