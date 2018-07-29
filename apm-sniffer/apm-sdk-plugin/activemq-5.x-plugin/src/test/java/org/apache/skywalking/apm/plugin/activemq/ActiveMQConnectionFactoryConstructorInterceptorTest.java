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

package org.apache.skywalking.apm.plugin.activemq;

import com.sun.org.apache.xerces.internal.util.URI;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(MockitoJUnitRunner.class)
public class ActiveMQConnectionFactoryConstructorInterceptorTest {

    @Mock
    private URI uri;


    private String uriPath;


    private    ActiveMQConnectionFactoryConstructorInterceptor  activeMQConnectionFactoryConstructorInterceptor;



    @Before
    public  void setUp()  throws Exception {
        activeMQConnectionFactoryConstructorInterceptor = new ActiveMQConnectionFactoryConstructorInterceptor();
        uri = new URI("failover://tcp://localhost:60601");
        uriPath = "failover://tcp://localhost:60601";
    }

    @Test
    public void testOnConsumer() {
        activeMQConnectionFactoryConstructorInterceptor.onConstruct(null, new Object[] {null,null,uri});
        assertThat(ActiveMQInfo.URL, is("localhost:60601"));
        activeMQConnectionFactoryConstructorInterceptor.onConstruct(null, new Object[] {null,null,uriPath});
        assertThat(ActiveMQInfo.URL, is("localhost:60601"));
    }



}