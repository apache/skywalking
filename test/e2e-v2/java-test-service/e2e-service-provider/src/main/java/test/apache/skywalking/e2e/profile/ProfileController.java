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

package test.apache.skywalking.e2e.profile;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.e2e.User;
import org.apache.skywalking.e2e.UserRepo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProfileController {
    private final UserRepo userRepo;
    private final GoProfileAgent goProfileAgent;

    @PostMapping("/profile/{name}")
    public User createAuthor(@RequestBody final CreateUser createUser) throws InterruptedException {
        final User user = userRepo.save(createUser.toUser());
        if (createUser.isEnableProfiling()) {
            TimeUnit.MILLISECONDS.sleep(6200);
        }
        return user;
    }

    @GetMapping("/profile")
    public String goProfile() {
        try {
            // Simulate some work that would trigger profiling
            performWork();
            goProfileAgent.triggerProfileCollection();
            
            return "Go profile data collected successfully";
        } catch (Exception e) {
            return "Error during Go profiling: " + e.getMessage();
        }
    }
    
    private void performWork() throws InterruptedException {
        // Simulate some CPU work that would be captured in profiling
        long start = System.currentTimeMillis();
        long sum = 0;
        for (int i = 0; i < 1000000; i++) {
            sum += Math.sqrt(i);
        }
        long duration = System.currentTimeMillis() - start;
        // Work completed - duration and sum are calculated for profiling simulation
        
        // Add some delay to ensure profiling captures this work
        Thread.sleep(100);
    }
}
