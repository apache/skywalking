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

package org.apache.skywalking.amp.testcase.undertow;

import io.undertow.Undertow;
import io.undertow.util.Headers;
import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class Application {

    private static final String CASE_URL = "/undertow-scenario/case/undertow";

    public static void main(String[] args) throws InterruptedException {
        Undertow server = Undertow.builder()
            .addHttpListener(8080, "127.0.0.1")
            .setHandler(exchange -> {
                if (CASE_URL.equals(exchange.getRequestPath())) {
                    visit("http://localhost:8080/undertow-scenario/case/undertow1");
                }
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                exchange.getResponseSender().send("Success");
            }).build();
        server.start();
    }

    private static void visit(String url) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet httpget = new HttpGet(url);
            ResponseHandler<String> responseHandler = response -> {
                HttpEntity entity = response.getEntity();
                return entity != null ? EntityUtils.toString(entity) : null;
            };
            httpclient.execute(httpget, responseHandler);
        } finally {
            httpclient.close();
        }
    }
}
