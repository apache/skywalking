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

package org.apache.skywalking.oap.server.cluster.plugin.kubernetes;

import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.ClusterRegister;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ClusterModuleKubernetesProviderTest {

    private ClusterModuleKubernetesProvider provider;

    @Before
    public void setUp() {
        provider = new ClusterModuleKubernetesProvider();
    }

    @Test
    public void assertName() {
        assertThat(provider.name(), is("kubernetes"));
    }

    @Test
    public void assertModule() {
        assertTrue(provider.module().isAssignableFrom(ClusterModule.class));
    }

    @Test
    public void assertCreateConfigBeanIfAbsent() {
        assertTrue(ClusterModuleKubernetesConfig.class.isInstance(provider.createConfigBeanIfAbsent()));
    }

    @Test
    public void assertPrepare() throws ServiceNotProvidedException {
        provider.prepare();
        ClusterRegister register = provider.getService(ClusterRegister.class);
        ClusterNodesQuery query = provider.getService(ClusterNodesQuery.class);
        assertSame(register, query);
        assertTrue(KubernetesCoordinator.class.isInstance(register));
    }
}