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

package org.apache.skywalking.oap.server.core.cluster;

import com.google.common.collect.Lists;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class OAPNodeCheckerTest {

    @Test
    public void hasIllegalNodeAddressWithNull() {
        boolean flag = OAPNodeChecker.hasIllegalNodeAddress(null);
        Assert.assertFalse(flag);
    }

    @Test
    public void hasIllegalNodeAddressWithEmptySet() {
        boolean flag = OAPNodeChecker.hasIllegalNodeAddress(Lists.newArrayList());
        Assert.assertFalse(flag);
    }

    @Test
    public void hasIllegalNodeAddressTrue() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        remoteInstances.add(new RemoteInstance(new Address("127.0.0.1", 8899, true)));
        remoteInstances.add(new RemoteInstance(new Address("123.23.4.2", 8899, true)));
        boolean flag = OAPNodeChecker.hasIllegalNodeAddress(remoteInstances);
        Assert.assertTrue(flag);
    }

    @Test
    public void hasIllegalNodeAddressFalse() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        remoteInstances.add(new RemoteInstance(new Address("123.23.4.2", 8899, true)));
        boolean flag = OAPNodeChecker.hasIllegalNodeAddress(remoteInstances);
        Assert.assertFalse(flag);
    }

    @Test
    public void unHealthWithEmptyInstance() {
        ClusterHealthStatus clusterHealthStatus = OAPNodeChecker.isHealth(Lists.newArrayList());
        Assert.assertFalse(clusterHealthStatus.isHealth());
    }

    @Test
    public void unHealthWithNullInstance() {
        ClusterHealthStatus clusterHealthStatus = OAPNodeChecker.isHealth(null);
        Assert.assertFalse(clusterHealthStatus.isHealth());
    }

    @Test
    public void unHealthWithEmptySelfInstance() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        remoteInstances.add(new RemoteInstance(new Address("192.168.0.1", 8892, false)));
        ClusterHealthStatus clusterHealthStatus = OAPNodeChecker.isHealth(remoteInstances);
        Assert.assertFalse(clusterHealthStatus.isHealth());
    }

    @Test
    public void unHealthWithIllegalNodeInstance() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        remoteInstances.add(new RemoteInstance(new Address("192.168.0.1", 8892, true)));
        remoteInstances.add(new RemoteInstance(new Address("127.0.0.1", 8892, true)));
        ClusterHealthStatus clusterHealthStatus = OAPNodeChecker.isHealth(remoteInstances);
        Assert.assertFalse(clusterHealthStatus.isHealth());
    }

    @Test
    public void healthWithOnlySelf() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        remoteInstances.add(new RemoteInstance(new Address("127.0.0.1", 8899, true)));
        ClusterHealthStatus clusterHealthStatus = OAPNodeChecker.isHealth(remoteInstances);
        Assert.assertTrue(clusterHealthStatus.isHealth());
    }

    @Test
    public void healthWithSelfAndNodes() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        remoteInstances.add(new RemoteInstance(new Address("192.168.0.1", 8899, true)));
        remoteInstances.add(new RemoteInstance(new Address("192.168.0.2", 8899, false)));
        ClusterHealthStatus clusterHealthStatus = OAPNodeChecker.isHealth(remoteInstances);
        Assert.assertTrue(clusterHealthStatus.isHealth());
    }
}