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
 */

package org.apache.skywalking.apm.collector.agent.jetty.provider;

import org.apache.skywalking.apm.collector.cluster.ModuleRegistration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class AgentModuleJettyRegistrationTest {

    private AgentModuleJettyRegistration registration;

    @Before
    public void setUp() throws Exception {
        registration = new AgentModuleJettyRegistration("127.0.0.1", 8080, "/");
    }

    @Test
    public void buildValue() {
        ModuleRegistration.Value value = registration.buildValue();
        Assert.assertEquals(value.getHostPort(), "127.0.0.1:8080");
        assertEquals(value.getContextPath(), "/");
    }
}