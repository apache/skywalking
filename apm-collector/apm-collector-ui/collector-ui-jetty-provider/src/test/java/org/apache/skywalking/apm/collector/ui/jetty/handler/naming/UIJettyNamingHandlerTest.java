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

package org.apache.skywalking.apm.collector.ui.jetty.handler.naming;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.Mockito.mock;

/**
 * @author lican
 */
public class UIJettyNamingHandlerTest {

    private UIJettyNamingHandler uiJettyNamingHandler;

    @Before
    public void setUp() {
        UIJettyNamingListener uiJettyNamingListener = new UIJettyNamingListener();
        uiJettyNamingListener.addAddress("127.0.0.1:10800");
        uiJettyNamingHandler = new UIJettyNamingHandler(uiJettyNamingListener);
    }

    @Test
    public void pathSpec() {
        Assert.assertEquals(uiJettyNamingHandler.pathSpec(), "/ui/jetty");
    }

    @Test
    public void doGet() throws ArgumentsParseException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        JsonElement jsonElement = uiJettyNamingHandler.doGet(request);
        Assert.assertTrue(jsonElement instanceof JsonArray);
        Assert.assertTrue(((JsonArray) jsonElement).size() > 0);

    }

    @Test(expected = UnsupportedOperationException.class)
    public void doPost() throws ArgumentsParseException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        uiJettyNamingHandler.doPost(request);

    }
}