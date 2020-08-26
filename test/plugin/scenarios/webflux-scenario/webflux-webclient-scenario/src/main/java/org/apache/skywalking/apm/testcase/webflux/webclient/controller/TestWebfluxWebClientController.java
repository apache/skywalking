/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.skywalking.apm.testcase.webflux.webclient.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
public class TestWebfluxWebClientController {
    @Value("${webclient.host:localhost:18081}")
    private String clientHostAddress;

    @RequestMapping("/testcase/webclient/server")
    public String webclientServer() {
        return "ok";
    }

    @RequestMapping("testcase/webclient/testGet")
    public String testGet() {
        Mono<String> response = WebClient
                .create()
                .get()
                .uri("http://" + clientHostAddress + "/testcase/webclient/server")
                .retrieve()
                .bodyToMono(String.class);
        response.subscribe();
        return "get:ok";
    }

    @RequestMapping("testcase/webclient/testPost")
    public String testPost() {
        Mono<String> response = WebClient
                .create()
                .post()
                .uri("http://" + clientHostAddress + "/testcase/webclient/server")
                .retrieve()
                .bodyToMono(String.class);
        response.subscribe();
        return "post:ok";
    }

}
