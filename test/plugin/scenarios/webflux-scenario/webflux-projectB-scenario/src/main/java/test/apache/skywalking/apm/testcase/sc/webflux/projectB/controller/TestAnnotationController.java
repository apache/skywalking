/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package test.apache.skywalking.apm.testcase.sc.webflux.projectB.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class TestAnnotationController {

    @RequestMapping("/testcase/annotation/healthCheck")
    public String healthCheck() {
        return "healthCheck";
    }

    @RequestMapping("/testcase/annotation/success")
    public String success() {
        return "1";
    }

    @RequestMapping("/testcase/annotation/error")
    public String error() {
        if (1 == 1) {
            throw new RuntimeException("test_error");
        }
        return "1";
    }

    @RequestMapping("/testcase/webclient/server")
    public String webclientServer() {
        return "success";
    }

    @GetMapping("/testcase/annotation/{test}")
    public Mono<String> urlPattern(@PathVariable("test") String var) {
        return Mono.just(var);
    }

    @GetMapping("/testcase/annotation/mono/hello")
    public Mono<String> hello(@RequestBody(required = false) String body) {
        return Mono.just("Hello World");
    }
}
