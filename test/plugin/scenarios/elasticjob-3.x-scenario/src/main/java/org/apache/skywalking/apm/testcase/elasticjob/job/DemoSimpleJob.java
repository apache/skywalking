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

package org.apache.skywalking.apm.testcase.elasticjob.job;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.shardingsphere.elasticjob.api.ShardingContext;
import org.apache.shardingsphere.elasticjob.simple.job.SimpleJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

@Component
public class DemoSimpleJob implements SimpleJob {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSimpleJob.class);
    OkHttpClient client = new OkHttpClient.Builder().build();

    private CountDownLatch latch = new CountDownLatch(1);

    public void getLatchAwait() throws InterruptedException {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw e;
        }
    }

    @Override
    public void execute(ShardingContext shardingContext) {
        LOGGER.info("Elastic Job Item: {} | Time: {} | Thread: {} | {}",
                shardingContext.getShardingItem(), new SimpleDateFormat("HH:mm:ss").format(new Date()), Thread.currentThread().getId(), "SIMPLE");
        Request request = new Request.Builder().url("http://localhost:8080/elasticjob-3.x-scenario/case/ping").build();
        Response response = null;
        latch.countDown();
        try {
            response = client.newCall(request).execute();
        } catch (IOException e) {
        }
        response.body().close();
    }
}
