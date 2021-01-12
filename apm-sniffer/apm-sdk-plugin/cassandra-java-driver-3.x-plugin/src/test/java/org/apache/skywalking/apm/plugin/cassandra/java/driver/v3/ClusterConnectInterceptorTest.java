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

import java.lang.reflect.Method;

@RunWith(PowerMockRunner.class)
public class ClusterConnectInterceptorTest {

    private ClusterConnectInterceptor interceptor;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private ConnectionInfo connectionInfo = new ConnectionInfo("localhost:9042");

        @Override
        public Object getSkyWalkingDynamicField() {
            return connectionInfo;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.connectionInfo = (ConnectionInfo) value;
        }
    };

    @Mock
    private Method method;

    @Before
    public void setUp() throws Exception {
        interceptor = new ClusterConnectInterceptor();
    }

    @Test
    public void afterMethod() throws Throwable {
        EnhancedInstance ret = (EnhancedInstance) interceptor.afterMethod(enhancedInstance, method, new Object[] {"test"}, null, enhancedInstance);
        ConnectionInfo connectionInfo = (ConnectionInfo) ret.getSkyWalkingDynamicField();
        Assert.assertThat(connectionInfo.getKeyspace(), Is.is("test"));
    }
}