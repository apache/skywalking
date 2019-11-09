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
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class Application {

    private static final String CASE_URL = "/undertow-scenario/case/undertow";

    private static final String TEMPLATE = "/undertow-routing-scenario/case/{context}";

    private static final String ROUTING_CASE_URL = "/undertow-routing-scenario/case/undertow";

    public static void main(String[] args) throws InterruptedException {
        new Thread(Application::undertowRouting).start();
        undertow();
    }

    private static void undertow() {
        Undertow server = Undertow.builder()
            .addHttpListener(8080, "0.0.0.0")
            .setHandler(exchange -> {
                if (CASE_URL.equals(exchange.getRequestPath())) {
                    exchange.dispatch(() -> {
                        try {
                            visit("http://localhost:8081/undertow-routing-scenario/case/undertow?send=httpHandler");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                exchange.getResponseSender().send("Success");
            }).build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    private static void undertowRouting() {
        HttpHandler httpHandler = exchange -> {
            if (ROUTING_CASE_URL.equals(exchange.getRequestPath())) {
                exchange.dispatch(httpServerExchange -> visit("http://localhost:8080/undertow-scenario/case/undertow1?send=runnable"));
            }
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("Success");
        };
        RoutingHandler handler = new RoutingHandler();
        handler.add(Methods.GET, TEMPLATE, httpHandler);
        handler.add(Methods.HEAD, TEMPLATE, httpHandler);
        Undertow server = Undertow.builder()
            .addHttpListener(8081, "0.0.0.0")
            .setHandler(handler).build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    private static void visit(String url) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet httpget = new HttpGet(url);
            ResponseHandler<String> responseHandler = response -> {
                HttpEntity entity = response.getEntity();
                return entity != null ? EntityUtils.toString(entity) : null;
            };
            httpClient.execute(httpget, responseHandler);
        } finally {
            httpClient.close();
        }
    }
}
