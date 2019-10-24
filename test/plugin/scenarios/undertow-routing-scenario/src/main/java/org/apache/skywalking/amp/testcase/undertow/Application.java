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

public class Application {

    private static final String template = "/undertow-routing-scenario/case/{context}";

    public static void main(String[] args) throws InterruptedException {
        HttpHandler httpHandler = exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("Success");
        };
        RoutingHandler handler = new RoutingHandler();
        handler.add(Methods.GET, template, httpHandler);
        handler.add(Methods.HEAD, template, httpHandler);
        Undertow server = Undertow.builder()
            .addHttpListener(8080, "0.0.0.0")
            .setHandler(handler).build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        // Waiting for service register, please do not delete.
        Thread.sleep(5000);
        server.start();
    }
}
