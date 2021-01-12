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

package org.apache.skywalking.oap.server.cluster.plugin.standalone;

import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.junit.Assert;
import org.junit.Test;

public class StandaloneManagerTest {
    @Test
    public void test() {
        StandaloneManager standaloneManager = new StandaloneManager();
        RemoteInstance remote1 = new RemoteInstance(new Address("A", 100, true));
        RemoteInstance remote2 = new RemoteInstance(new Address("B", 100, false));

        standaloneManager.registerRemote(remote1);
        Assert.assertEquals(remote1, standaloneManager.queryRemoteNodes().get(0));
        standaloneManager.registerRemote(remote2);
        Assert.assertEquals(remote2, standaloneManager.queryRemoteNodes().get(0));
    }
}
