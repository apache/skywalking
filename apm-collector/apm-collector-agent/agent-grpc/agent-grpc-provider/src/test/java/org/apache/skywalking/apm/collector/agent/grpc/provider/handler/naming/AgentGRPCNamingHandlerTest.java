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

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler.naming;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertEquals;

/**
 * @author lican
 */
public class AgentGRPCNamingHandlerTest {

    private AgentGRPCNamingHandler handler;

    @Mock
    private HttpServletRequest request;

    @Before
    public void setUp() throws Exception {
        AgentGRPCNamingListener listener = new AgentGRPCNamingListener();
        listener.addAddress("127.0.0.1:10080");
        handler = new AgentGRPCNamingHandler(listener);

    }

    @Test
    public void pathSpec() {
        assertEquals(handler.pathSpec(), "/agent/gRPC");
    }

    @Test
    public void doGet() throws ArgumentsParseException {
        JsonElement jsonElement = handler.doGet(request);
        String s = ((JsonArray) jsonElement).get(0).getAsString();
        assertEquals(s, "127.0.0.1:10080");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void doPost() throws ArgumentsParseException {
        handler.doPost(request);
    }
}