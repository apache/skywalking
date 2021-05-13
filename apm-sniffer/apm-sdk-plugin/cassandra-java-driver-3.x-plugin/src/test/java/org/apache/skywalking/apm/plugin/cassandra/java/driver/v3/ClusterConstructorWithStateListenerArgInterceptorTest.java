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

package org.apache.skywalking.apm.plugin.cassandra.java.driver.v3;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.hamcrest.core.Is;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

@RunWith(PowerMockRunner.class)
public class ClusterConstructorWithStateListenerArgInterceptorTest {

    @Mock
    private ClusterConstructorWithStateListenerArgInterceptor interceptor;

    @Mock
    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private ConnectionInfo connectionInfo;

        @Override
        public Object getSkyWalkingDynamicField() {
            return connectionInfo;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.connectionInfo = (ConnectionInfo) value;
        }
    };

    private List<InetSocketAddress> inetSocketAddresses;

    @Before
    public void setUp() throws Exception {
        interceptor = new ClusterConstructorWithStateListenerArgInterceptor();

        inetSocketAddresses = new ArrayList<InetSocketAddress>();
        inetSocketAddresses.add(new InetSocketAddress("172.20.0.2", 9042));
    }

    @Test
    public void onConstruct() {
        interceptor.onConstruct(enhancedInstance, new Object[] {
            "cluster-name",
            inetSocketAddresses
        });
        ConnectionInfo connectionInfo = (ConnectionInfo) enhancedInstance.getSkyWalkingDynamicField();
        Assert.assertThat(connectionInfo.getContactPoints(), Is.is("172.20.0.2:9042"));
    }
}