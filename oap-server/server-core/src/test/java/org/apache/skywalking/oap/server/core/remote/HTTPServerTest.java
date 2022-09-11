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
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.annotation.Post;
import java.util.Collections;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class HTTPServerTest {
    static HTTPServer SERVER;

    @BeforeClass
    public static void beforeTest() {
        HTTPServerConfig config = HTTPServerConfig.builder()
                                                  .host("0.0.0.0")
                                                  .port(12800)
                                                  .contextPath("/")
                                                  .idleTimeOut(5000)
                                                  .build();

        SERVER = new HTTPServer(config);
        SERVER.initialize();
        SERVER.addHandler(new TestPostHandler(), Collections.singletonList(HttpMethod.POST));
        SERVER.start();
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
    }

    static class TestPostHandler {
        @Post("/test")
        public HttpResponse doPost() {
            return HttpResponse.of(HttpStatus.OK);
        }
    }
}
