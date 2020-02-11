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

package org.apache.skywalking.apm.plugin.xmemcached.v2;

import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class XMemcachedConstructorWithInetSocketAddressArgInterceptorTest {

    private XMemcachedConstructorWithInetSocketAddressArgInterceptor interceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() throws Exception {
        interceptor = new XMemcachedConstructorWithInetSocketAddressArgInterceptor();
    }

    @Test
    public void onConstructWithInetSocketAddress() {
        interceptor.onConstruct(enhancedInstance, new Object[] {new InetSocketAddress("127.0.0.1", 11211)});

        verify(enhancedInstance).setSkyWalkingDynamicField("127.0.0.1:11211");
    }
}
