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
package org.apache.skywalking.apm.testcase.webflux.controller;

import java.io.IOException;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Born
 */
@RestController
public class TestCaseController {


    @GetMapping("/testcase")
    public Mono<Boolean> testcase() {
        return Flux.concat(
                WebClient.create("http://localhost:8080/testcase/annotation/hello").get().exchange(),
                WebClient.create("http://localhost:8080/testcase/annotation/bad").get().exchange(),
                WebClient.create("http://localhost:8080/testcase/route/hello").get().exchange(),
                WebClient.create("http://localhost:8080/testcase/route/bad").get().exchange(),
                WebClient.create("http://localhost:8080/testcase/notFound").get().exchange()
                ).all(Objects::nonNull);
    }

    @GetMapping("/healthCheck")
    public Mono<String> healthCheck() throws IOException {
        return Mono.just("test");
    }


    @GetMapping("/testcase/annotation/hello")
    public Mono<String> testHello() {
        return Mono.just("hello");
    }

    @GetMapping("/testcase/annotation/bad")
    public Mono<String> testBad() {
        throw new RuntimeException();
    }

}
