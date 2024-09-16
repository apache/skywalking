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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.IOException;
import java.net.URL;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.apache.skywalking.e2e.User;
import org.apache.skywalking.e2e.UserRepo;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
@RequiredArgsConstructor
@SuppressWarnings("SameReturnValue")
public class UserController {
    private static final org.slf4j.Logger LOGBACK_LOGGER = LoggerFactory.getLogger(UserController.class);

    private final Cache<String, String> guavaCache = CacheBuilder.newBuilder()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .build();

    private final UserRepo userRepo;
    private final int sleepMin = 500;
    private final int sleepMax = 1000;

    @RequestMapping(value = "/info", method = {RequestMethod.POST, RequestMethod.GET})
    public String info() throws InterruptedException {
        Thread.sleep(randomSleepLong(sleepMin, sleepMax));
        LOGBACK_LOGGER.info("logback message==> now: {}", System.currentTimeMillis());
        return "whatever";
    }

    @PostMapping("/users")
    public User createAuthor(@RequestBody final User user) throws InterruptedException, IOException {
        Thread.sleep(randomSleepLong(sleepMin, sleepMax));
        new URL("http://localhost:9090/agent-so11y-scenario/case/ignore.html").getContent();
        //virtual cache test case
        testCacheService();
        return userRepo.save(user);
    }

    @GetMapping("/ignore.html")
    public String ignore() {
        return "success";
    }

    @PostMapping("/correlation")
    public String correlation() throws InterruptedException {
        Thread.sleep(randomSleepLong(sleepMin, sleepMax));
        TraceContext.putCorrelation("PROVIDER_KEY", "provider");
        return TraceContext.getCorrelation("CONSUMER_KEY").orElse("") + "_"
            + TraceContext.getCorrelation("MIDDLE_KEY").orElse("") + "_"
            + TraceContext.getCorrelation("PROVIDER_KEY").orElse("");
    }

    private long randomSleepLong(int min, int max) {
        Random rand = new Random();
        int randomNumber = rand.nextInt((max - min) + 1) + min;
        return randomNumber;
    }

    private void testCacheService() {
        String userInfo = guavaCache.getIfPresent("user_1");
        if (!StringUtils.hasLength(userInfo)) {
            guavaCache.put("user_1", "name:John,address:earth");
        }
        Map<String, String> users = new HashMap<>();
        users.put("user_2", "name:Tom,address:earth");
        users.put("user_3", "name:Jack,address:earth");
        guavaCache.putAll(users);
        guavaCache.getAllPresent(Arrays.asList("user_2", "user_3"));
        guavaCache.invalidate("user_1");
        guavaCache.invalidate("user_2");
        guavaCache.invalidateAll();
    }
}
