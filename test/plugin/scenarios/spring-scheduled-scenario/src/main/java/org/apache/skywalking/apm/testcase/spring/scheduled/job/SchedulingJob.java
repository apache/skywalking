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

package org.apache.skywalking.apm.testcase.spring.scheduled.job;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;

@Configuration
@EnableScheduling
public class SchedulingJob {

    private static final Logger LOGGER = LogManager.getLogger(SchedulingJob.class);
    
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().build();

    @Scheduled(fixedDelay = 5000)
    public void work() throws IOException {
        LOGGER.info("work job running!");

        Request request = new Request.Builder().url("http://localhost:8080/spring-scheduled-scenario/case/call").build();
        Response response = CLIENT.newCall(request).execute();
        response.body().close();
    }
}
