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

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.e2e.User;
import org.apache.skywalking.e2e.UserRepo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequiredArgsConstructor
@SuppressWarnings("SameReturnValue")
public class UserController {
    private final UserRepo userRepo;
    private final int sleepMin = 500;
    private final int sleepMax = 1000;

    @PostMapping("/info")
    public String info() throws InterruptedException {
        Thread.sleep(randomSleepLong(sleepMin, sleepMax));
        return "whatever";
    }

    @PostMapping("/users")
    public User createAuthor(@RequestBody final User user) throws InterruptedException {
        Thread.sleep(randomSleepLong(sleepMin, sleepMax));
        return userRepo.save(user);
    }

    private long randomSleepLong(int min, int max) {
        Random rand = new Random();
        int randomNumber = rand.nextInt((max - min) + 1) + min;
        return randomNumber;
    }
}
