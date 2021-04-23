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

package org.apache.skywalking.apm.testcase.quartzscheduler.job;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

import java.io.IOException;

@Slf4j
public class DemoJob implements Job {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().build();

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        log.info("quartz job execute: {}", jobExecutionContext.getJobDetail().getKey());

        Request request = new Request.Builder().url("http://localhost:8080/quartz-scheduler-2.x-scenario/case/call").build();
        Response response = null;
        try {
            response = CLIENT.newCall(request).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        response.body().close();
    }
}
