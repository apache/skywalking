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

package org.apache.skywalking.apm.webapp;

import org.apache.skywalking.apm.webapp.proxy.NotFoundHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@RunWith(SpringRunner.class)
public class WebAppTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private NotFoundHandler notFoundHandler;

    @Test
    public void shouldGetStaticResources() throws Exception {
        when(notFoundHandler.renderDefaultPage()).thenCallRealMethod();

        mvc.perform(get("/index.html"))
           .andDo(print())
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("<title>SkyWalking</title>")));

        verify(notFoundHandler, never()).renderDefaultPage();
    }

    @Test
    public void shouldRedirectToIndexWhenResourcesIsAbsent() throws Exception {
        when(notFoundHandler.renderDefaultPage()).thenCallRealMethod();

        mvc.perform(get("/absent.html")).andDo(print()).andExpect(status().isOk());

        verify(notFoundHandler, only()).renderDefaultPage();
    }
}