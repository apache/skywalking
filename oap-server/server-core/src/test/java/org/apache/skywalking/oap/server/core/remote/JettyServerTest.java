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

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
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
    public void test() {

        String rootURI = "http://localhost:12800";
        String testHandlerURI = "http://localhost:12800/test";
        String testNoHandlerURI = "http://localhost:12800/test/noHandler";
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpGet httpGet = new HttpGet(rootURI);
        HttpPost httpPost = new HttpPost(rootURI);
        HttpPost httpPostTestHandler = new HttpPost(testHandlerURI);
        HttpPost httpPostTestNoHandler = new HttpPost(testNoHandlerURI);
        HttpTrace httpTrace = new HttpTrace(testHandlerURI);
        HttpTrace httpTraceRoot = new HttpTrace(rootURI);
        HttpPut httpPut = new HttpPut(testHandlerURI);
        HttpDelete httpDelete = new HttpDelete(testHandlerURI);
        HttpOptions httpOptions = new HttpOptions(testHandlerURI);
        HttpHead httpHead = new HttpHead(testHandlerURI);
        CloseableHttpResponse response = null;
        try {
            //get
            response = httpClient.execute(httpGet);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 405);
            response.close();

            //post root
            response = httpClient.execute(httpPost);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 404);
            response.close();

            //post
            response = httpClient.execute(httpPostTestHandler);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 200);
            response.close();

            //post no handler
            response = httpClient.execute(httpPostTestNoHandler);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 404);
            response.close();

            //trace
            response = httpClient.execute(httpTrace);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 405);
            response.close();

            //trace root
            response = httpClient.execute(httpTraceRoot);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 405);
            response.close();

            //put
            response = httpClient.execute(httpPut);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 405);
            response.close();

            //delete
            response = httpClient.execute(httpDelete);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 405);
            response.close();

            //options
            response = httpClient.execute(httpOptions);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 405);
            response.close();

            //head
            response = httpClient.execute(httpHead);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 405);
            response.close();
        } catch (IOException e) {
            Assert.fail("Test failed!");
            e.printStackTrace();
        } finally {
            try {
                if (httpClient != null) {
                    httpClient.close();
                }
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class TestPostHandler extends JettyHandler {

        @Override
        public String pathSpec() {
            return "/test";
        }

        @Override
        protected void doPost(final HttpServletRequest req,
                              final HttpServletResponse resp) throws ServletException, IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
        }

    }
}
