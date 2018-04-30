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

package org.apache.skywalking.apm.collector.agent.jetty.provider.handler.naming;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertEquals;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class AgentJettyNamingHandlerTest {

    private AgentJettyNamingHandler agentJettyNamingHandler;
    @Mock
    private HttpServletRequest request;

    private String address = "127.0.0.1:8080";

    @Before
    public void setUp() {
        AgentJettyNamingListener agentJettyNamingListener = new AgentJettyNamingListener();
        agentJettyNamingListener.addAddress(address);
        agentJettyNamingHandler = new AgentJettyNamingHandler(agentJettyNamingListener);

    }

    @Test
    public void pathSpec() {
        assertEquals(agentJettyNamingHandler.pathSpec(), "/agent/jetty");
    }

    @Test
    public void doGet() throws ArgumentsParseException {
        JsonElement jsonElement = agentJettyNamingHandler.doGet(request);
        assertEquals(((JsonArray) jsonElement).get(0).getAsString(), address);

    }

    @Test(expected = UnsupportedOperationException.class)
    public void doPost() throws ArgumentsParseException {
        agentJettyNamingHandler.doPost(request);
    }
}