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

package org.apache.skywalking.oap.server.core.remote;

import com.linecorp.armeria.client.WebClient;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.oap.server.library.server.jetty.JettyHandler;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServer;
import org.apache.skywalking.oap.server.library.server.jetty.JettyServerConfig;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

public class JettyServerTest {
    static JettyServer SERVER;

    @BeforeClass
    public static void beforeTest() throws Exception {
        JettyServerConfig config = JettyServerConfig.builder()
                                                    .host("0.0.0.0")
                                                    .port(12800)
                                                    .contextPath("/")
                                                    .jettyIdleTimeOut(5000)
                                                    .build();

        SERVER = new JettyServer(config);
        SERVER.initialize();
        SERVER.addHandler(new TestPostHandler());
        SERVER.start();

    }

    @AfterClass
    public static void afterTest() throws Exception {
        ((org.eclipse.jetty.server.Server) Whitebox.getInternalState(SERVER, "server")).stop();
    }

    @Test
    public void test() throws Exception {

        String rootURI = "http://localhost:12800";
        String testHandlerURI = "http://localhost:12800/test";
        String testNoHandlerURI = "http://localhost:12800/test/noHandler";

        Assert.assertEquals(
            WebClient.of().get(rootURI).aggregate().get().status().code(),
            405
        );

        Assert.assertEquals(
            WebClient.of().post(rootURI, new byte[0]).aggregate().get().status().code(),
            404
        );

        Assert.assertEquals(
            WebClient.of().post(testHandlerURI, new byte[0]).aggregate().get().status().code(),
            200
        );

        Assert.assertEquals(
            WebClient.of().post(testNoHandlerURI, new byte[0]).aggregate().get().status()
                     .code(),
            404
        );

        Assert.assertEquals(
            WebClient.of().trace(testNoHandlerURI).aggregate().get().status().code(),
            405
        );

        Assert.assertEquals(
            WebClient.of().trace(rootURI).aggregate().get().status().code(),
            405
        );

        Assert.assertEquals(
            WebClient.of().put(testHandlerURI, new byte[0]).aggregate().get().status().code(),
            405
        );

        Assert.assertEquals(
            WebClient.of().delete(testHandlerURI).aggregate().get().status().code(),
            405
        );

        Assert.assertEquals(
            WebClient.of().options(testHandlerURI).aggregate().get().status().code(),
            405
        );

        Assert.assertEquals(
            WebClient.of().head(testHandlerURI).aggregate().get().status().code(),
            405
        );
    }

    static class TestPostHandler extends JettyHandler {

        @Override
        public String pathSpec() {
            return "/test";
        }

        @Override
        protected void doPost(final HttpServletRequest req,
                              final HttpServletResponse resp) {
            resp.setStatus(HttpServletResponse.SC_OK);
        }

    }
}
