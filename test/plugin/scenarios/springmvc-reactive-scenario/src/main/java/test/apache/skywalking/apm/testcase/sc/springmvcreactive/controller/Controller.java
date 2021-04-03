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

package test.apache.skywalking.apm.testcase.sc.springmvcreactive.controller;

import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;
import reactor.core.publisher.Mono;
import test.apache.skywalking.apm.testcase.sc.springmvcreactive.service.TestService;

@RestController
public class Controller {

    @Autowired
    private TestService testService;

    @RequestMapping("/testcase/healthCheck")
    public String healthCheck() {
        return "healthCheck";
    }

    @GetMapping("/testcase/{test}")
    public Mono<String> hello(@RequestBody(required = false) String body, @PathVariable("test") String test) throws SQLException {
        testService.executeSQL();
        ListenableFuture<ResponseEntity<String>> forEntity = new AsyncRestTemplate().getForEntity("http://localhost:8080/testcase/error", String.class);
        try {
            forEntity.get();
        } catch (Exception e) {
        }
        return Mono.just("Hello World");
    }

    @GetMapping("/testcase/error")
    public Mono<String> error() {
        throw new RuntimeException("this is Error");
    }

}
