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

package org.apache.skywalking.apm.testcase.lettuce.controller;

import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/case")
@PropertySource("classpath:application.properties")
public class LettuceController {

    @Value("${redis.servers:127.0.0.1:6379}")
    private String address;

    @RequestMapping("/lettuce-case")
    @ResponseBody
    public String lettuceCase() {
        RedisClient redisClient = RedisClient.create("redis://" + address);
        StatefulRedisConnection<String, String> connection0 = redisClient.connect();
        RedisCommands<String, String> syncCommand = connection0.sync();
        syncCommand.get("key");

        StatefulRedisConnection<String, String> connection1 = redisClient.connect();
        RedisAsyncCommands<String, String> asyncCommands = connection1.async();
        asyncCommands.setAutoFlushCommands(false);
        List<RedisFuture<?>> futures = new ArrayList<>();
        futures.add(asyncCommands.set("key0", "value0"));
        futures.add(asyncCommands.set("key1", "value1"));
        asyncCommands.flushCommands();
        LettuceFutures.awaitAll(5, TimeUnit.SECONDS, futures.toArray(new RedisFuture[futures.size()]));

        connection0.close();
        connection1.close();
        redisClient.shutdown();
        return "Success";
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return "healthCheck";
    }
}
