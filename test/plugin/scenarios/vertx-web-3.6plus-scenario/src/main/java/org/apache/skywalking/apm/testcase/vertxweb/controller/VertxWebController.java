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

package org.apache.skywalking.apm.testcase.vertxweb.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;

public class VertxWebController extends AbstractVerticle {

    @Override
    public void start() {
        Router router = Router.router(vertx);
        router.get("/vertx-web-3_6plus-scenario/case/web-case").handler(this::handleWebCase);
        router.get("/vertx-web-3_6plus-scenario/dynamicEndpoint/:id").handler(this::dynamicEndpoint);
        router.get("/vertx-web-3_6plus-scenario/case/web-case/withBodyHandler")
                .handler(BodyHandler.create()).handler(this::withBodyHandler);
        router.head("/vertx-web-3_6plus-scenario/case/healthCheck").handler(this::healthCheck);
        vertx.createHttpServer().requestHandler(router).listen(8080);
    }

    private void handleWebCase(RoutingContext routingContext) {
        //dynamic endpoint test
        WebClient.create(vertx).get(8080, "localhost",
                "/vertx-web-3_6plus-scenario/dynamicEndpoint/100").send(it -> {
        });

        //non-body and body handler test
        WebClient.create(vertx).head(8080, "localhost", "/vertx-web-3_6plus-scenario/case/healthCheck")
                .send(healthCheck -> {
                    if (healthCheck.succeeded()) {
                        WebClient.create(vertx).get(8080, "localhost", "/vertx-web-3_6plus-scenario/case/web-case/withBodyHandler")
                                .send(it -> routingContext.response().setStatusCode(it.result().statusCode()).end());
                    } else {
                        healthCheck.cause().printStackTrace();
                        routingContext.response().setStatusCode(500).end();
                    }
                });
    }

    private void dynamicEndpoint(RoutingContext routingContext) {
        routingContext.response().setStatusCode(200).end("Success");
    }

    private void withBodyHandler(RoutingContext routingContext) {
        routingContext.response().setStatusCode(200).end("Success");
    }

    private void healthCheck(RoutingContext routingContext) {
        routingContext.response().setStatusCode(200).end("Success");
    }
}
