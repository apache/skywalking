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
 */

package test.apache.skywalking.apm.testcase.jdk.threading;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @RestController
    static class TestController {
        private final RestTemplate restTemplate;
        private final ExecutorService executorService;

        public TestController(final RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
            this.executorService = Executors.newSingleThreadScheduledExecutor();
        }

        @GetMapping("/healthCheck")
        public String healthCheck() {
            return "Success";
        }

        @GetMapping("/greet/{username}")
        public String testCase(@PathVariable final String username) throws ExecutionException, InterruptedException {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    restTemplate.getForEntity("https://github.com/apache/skywalking", String.class);
                }
            };

            executorService.execute(runnable);

            executorService.submit(new Callable<String>() {
                @Override
                public String call() {
                    return restTemplate.getForEntity("https://github.com/apache/skywalking", String.class).getBody();
                }
            }).get();

            return username;
        }
    }
}
