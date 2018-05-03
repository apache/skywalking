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

package org.apache.skywalking.apm.collector.agent.jetty.provider.handler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.collector.DelegatingServletInputStream;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class InstanceDiscoveryServletHandlerTest {

    @Mock
    private ModuleManager moduleManager;
    @Mock
    private HttpServletRequest request;

    private Gson gson = new Gson();

    private InstanceDiscoveryServletHandler instanceDiscoveryServletHandler;

    @Before
    public void setUp() throws Exception {
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        instanceDiscoveryServletHandler = new InstanceDiscoveryServletHandler(moduleManager);

    }

    @Test
    public void pathSpec() {
        assertEquals(instanceDiscoveryServletHandler.pathSpec(), "/instance/register");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void doGet() throws ArgumentsParseException {
        instanceDiscoveryServletHandler.doGet(request);
    }

    @Test
    public void doPost() throws ArgumentsParseException, IOException {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("ai", 1);
        jsonObject.addProperty("au", "123");
        jsonObject.addProperty("rt", System.currentTimeMillis());
        JsonObject osInfo = new JsonObject();
        osInfo.addProperty("osName", "test");
        jsonObject.add("oi", osInfo);
        String s = gson.toJson(jsonObject);
        Mockito.when(request.getReader()).then(invocation -> {
            DelegatingServletInputStream delegatingServletInputStream = new DelegatingServletInputStream(new ByteArrayInputStream(s.getBytes()));
            return new BufferedReader(new InputStreamReader(delegatingServletInputStream));
        });
        JsonElement jsonElement = instanceDiscoveryServletHandler.doPost(request);
        int ii = ((JsonObject) jsonElement).get("ii").getAsInt();
        assertEquals(ii, 0);
    }
}