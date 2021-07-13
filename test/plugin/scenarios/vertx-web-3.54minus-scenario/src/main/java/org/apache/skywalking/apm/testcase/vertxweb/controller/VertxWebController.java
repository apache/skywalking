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
import io.vertx.ext.web.handler.BodyHandler;

public class VertxWebController extends AbstractVerticle {

    @Override
    public void start() {
        Router router = Router.router(vertx);
        router.get("/vertx-web-3_54minus-scenario/case/web-case").handler(this::handleWebCase);
        router.get("/vertx-web-3_54minus-scenario/dynamicEndpoint/:id").handler(this::dynamicEndpoint);
        router.route("/vertx-web-3_54minus-scenario/case/web-case/withBodyHandler").handler(BodyHandler.create());
        router.get("/vertx-web-3_54minus-scenario/case/web-case/withBodyHandler").handler(this::withBodyHandler);
        router.head("/vertx-web-3_54minus-scenario/case/healthCheck").handler(this::healthCheck);
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }

    private void handleWebCase(RoutingContext routingContext) {
        //dynamic endpoint test
        vertx.createHttpClient().getNow(8080, "localhost",
                "/vertx-web-3_54minus-scenario/dynamicEndpoint/100", it -> {
                });

        //non-body and body handler test
        vertx.createHttpClient().headNow(8080, "localhost", "/vertx-web-3_54minus-scenario/case/healthCheck",
                healthCheck -> {
                    vertx.createHttpClient().getNow(8080, "localhost",
                            "/vertx-web-3_54minus-scenario/case/web-case/withBodyHandler",
                            it -> routingContext.response().setStatusCode(it.statusCode()).end());
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
