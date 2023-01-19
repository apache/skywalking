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

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientFactoryBuilder;
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

public class HTTPServerTestForTLS {
    static HTTPServer SERVER;

    @BeforeClass
    public static void beforeTest() {
        HTTPServerConfig config = HTTPServerConfig.builder()
                .host("0.0.0.0")
                .contextPath("/")
                .idleTimeOut(5000)
                .httpsPort(12801)
                .enableTLS(true)
                .enableTlsSelfSigned(true)
                .build();

        SERVER = new HTTPServer(config);
        SERVER.initialize();
        SERVER.addHandler(new TestPostHandler(), Collections.singletonList(HttpMethod.POST));
        SERVER.start();
    }

    @Test
    public void test() throws Exception {

        String rootURI = "https://localhost:12801";

        ClientFactoryBuilder cfb = ClientFactory.builder();
        cfb.tlsNoVerify();
        ClientFactory cf = cfb.build();
        WebClient client = WebClient.builder(rootURI)
                .factory(cf)
                .build();
        int code = client.post("/test", new byte[0])
                .aggregate()
                .get()
                .status()
                .code();
        Assert.assertEquals(code, 200);
    }

    static class TestPostHandler {
        @Post("/test")
        public HttpResponse doPost() {
            return HttpResponse.of(HttpStatus.OK);
        }
    }
}
