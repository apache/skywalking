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

import com.google.common.collect.Sets;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class OAPNodeCheckerTest {

    @Test
    public void hasUnHealthAddressFalse() {
        Set<String> address = Sets.newHashSet("123.23.4.2");
        boolean flag = OAPNodeChecker.hasUnHealthAddress(address);
        Assert.assertFalse(flag);
    }

    @Test
    public void hasUnHealthAddressWithNull() {
        Set<String> address = null;
        boolean flag = OAPNodeChecker.hasUnHealthAddress(address);
        Assert.assertFalse(flag);
    }

    @Test
    public void hasUnHealthAddressWithEmptySet() {
        Set<String> address = Sets.newHashSet();
        boolean flag = OAPNodeChecker.hasUnHealthAddress(address);
        Assert.assertFalse(flag);
    }

    @Test
    public void hasUnHealthAddressTrue() {
        Set<String> address = Sets.newHashSet("123.23.4.2", "127.0.0.1");
        boolean flag = OAPNodeChecker.hasUnHealthAddress(address);
        Assert.assertTrue(flag);
    }

    @Test
    public void hasDuplicateSelfAddressTrue() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        remoteInstances.add(new RemoteInstance(new Address("127.0.0.1", 8899, true)));
        remoteInstances.add(new RemoteInstance(new Address("192.168.0.1", 8892, true)));
        boolean flag = OAPNodeChecker.hasDuplicateSelfAddress(remoteInstances);
        Assert.assertTrue(flag);
    }

    @Test
    public void hasDuplicateSelfAddressFalse() {
        List<RemoteInstance> remoteInstances = new ArrayList<>();
        remoteInstances.add(new RemoteInstance(new Address("127.0.0.1", 8899, true)));
        boolean flag = OAPNodeChecker.hasDuplicateSelfAddress(remoteInstances);
        Assert.assertFalse(flag);
    }
}
