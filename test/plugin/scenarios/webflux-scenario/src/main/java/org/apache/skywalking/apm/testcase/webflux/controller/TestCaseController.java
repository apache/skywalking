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

import java.time.Duration;
import java.util.Objects;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * @author Born
 */
@RestController
public class TestCaseController {

    @GetMapping("/testcase")
    public Mono<Boolean> testcase() {
        return Flux.concat(
                Mono.fromRunnable(() -> visit("http://localhost:8080/testcase/annotation/hello", "GET")).timeout(Duration.ofMinutes(1)),
                Mono.fromRunnable(() -> visit("http://localhost:8080/testcase/annotation/bad", "GET")).timeout(Duration.ofMinutes(1)),
                Mono.fromRunnable(() -> visit("http://localhost:8080/testcase/annotation/foo", "POST")).timeout(Duration.ofMinutes(1)),
                Mono.fromRunnable(() -> visit("http://localhost:8080/testcase/annotation/loo", "POST")).timeout(Duration.ofMinutes(1)),
                Mono.fromRunnable(() -> visit("http://localhost:8080/testcase/route/hello", "GET")).timeout(Duration.ofMinutes(1)),
                Mono.fromRunnable(() -> visit("http://localhost:8080/testcase/route/bad", "GET")).timeout(Duration.ofMinutes(1)),
                Mono.fromRunnable(() -> visit("http://localhost:8080/testcase/route/foo", "POST")).timeout(Duration.ofMinutes(1)),
                Mono.fromRunnable(() -> visit("http://localhost:8080/testcase/route/loo", "POST")).timeout(Duration.ofMinutes(1)),
                Mono.fromRunnable(() -> visit("http://localhost:8080/testcase/notFound", "GET")).timeout(Duration.ofMinutes(1))
        ).all(Objects::nonNull).timeout(Duration.ofMinutes(10)).subscribeOn(Schedulers.parallel());
    }

    @GetMapping("/healthCheck")
    public Mono<String> healthCheck() {
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

    @PostMapping("/testcase/annotation/{test}")
    public Mono<String> testVariable(@PathVariable("test") String var) {
        return Mono.just(var);
    }



    private static void visit(String url, String method) {
        OkHttpClient okHttpClient = new OkHttpClient();
        try {
            Request request ;
            if ("GET".equals(method)) {
                request = new Request.Builder().url(url).get().build();
            } else {
                request = new Request.Builder().url(url).post(RequestBody.create(MediaType.parse("text/html"),"")).build();
            }
            okHttpClient.newCall(request).execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
