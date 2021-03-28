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

package org.apache.skywalking.e2e.controller;

import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.apache.skywalking.e2e.E2EConfiguration;
import org.apache.skywalking.e2e.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequiredArgsConstructor
public class UserController {
    private final RestTemplate restTemplate = new RestTemplate();

    private final E2EConfiguration configuration;

    private final int sleepMin = 500;

    private final int sleepMax = 1000;

    @PostMapping("/info")
    public String info() throws InterruptedException {
        Thread.sleep(randomSleepLong(sleepMin, sleepMax));

        Optional<ResponseEntity<String>> optionalResponseEntity = Stream.of(
            Strings.nullToEmpty(configuration.getProviderBaseUrl()).split(","))
                                                                        .map(baseUrl -> restTemplate.postForEntity(
                                                                            baseUrl + "/info", null, String.class))
                                                                        .findFirst();
        if (optionalResponseEntity.isPresent() && optionalResponseEntity.get().getStatusCodeValue() == 200) {
            return optionalResponseEntity.get().getBody();
        }
        throw new RuntimeException();
    }

    @PostMapping("/users")
    public Object createAuthor(@RequestBody final User user) throws InterruptedException {
        Thread.sleep(randomSleepLong(sleepMin, sleepMax));

        return Stream.of(Strings.nullToEmpty(configuration.getProviderBaseUrl()).split(","))
                     .map(baseUrl -> restTemplate.postForEntity(baseUrl + "/users", user, User.class))
                     .collect(Collectors.toList());
    }

    @PostMapping("/correlation")
    public String correlation() throws InterruptedException {
        Thread.sleep(randomSleepLong(sleepMin, sleepMax));
        TraceContext.putCorrelation("CONSUMER_KEY", "consumer");

        String baseUrl = configuration.getProviderBaseUrl();
        ResponseEntity<String> resp = restTemplate.postForEntity(baseUrl + "/correlation", null, String.class);
        return resp.getBody();
    }

    private long randomSleepLong(int min, int max) {
        Random rand = new Random();
        int randomNumber = rand.nextInt((max - min) + 1) + min;
        return randomNumber;
    }
}
