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

package org.apache.skywalking.apm.plugin.mongodb.v3.interceptor.v37;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class MongoDBClientDelegateInterceptorTest {

    private MongoDBClientDelegateInterceptor interceptor;

    @Mock
    private EnhancedInstance clientDelegateEnhancedInstance;

    private EnhancedInstance retEnhancedInstance;

    private final static String REMOTE_PEER = "127.0.0.1:27017";

    @Before
    public void setUp() {
        interceptor = new MongoDBClientDelegateInterceptor();
        retEnhancedInstance = new FieldEnhancedInstance();
        when(clientDelegateEnhancedInstance.getSkyWalkingDynamicField()).thenReturn(REMOTE_PEER);
    }

    @Test
    public void testAfterMethod() {
        interceptor.afterMethod(clientDelegateEnhancedInstance, null, null, null, retEnhancedInstance);
        Assert.assertEquals(REMOTE_PEER, retEnhancedInstance.getSkyWalkingDynamicField());
    }

    private static class FieldEnhancedInstance implements EnhancedInstance {

        private Object skyWalkingDynamicField;

        @Override
        public Object getSkyWalkingDynamicField() {
            return skyWalkingDynamicField;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.skyWalkingDynamicField = value;
        }
    }

}
