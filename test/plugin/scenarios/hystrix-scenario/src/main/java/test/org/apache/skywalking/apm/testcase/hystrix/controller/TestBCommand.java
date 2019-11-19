/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package test.apache.skywalking.apm.testcase.hystrix.controller;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestBCommand extends HystrixCommand<String> {
    private Logger logger = LogManager.getLogger(TestACommand.class);

    private String name;

    protected TestBCommand(String name) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestBCommand"))
            .andCommandPropertiesDefaults(
                HystrixCommandProperties.Setter()
                    .withExecutionTimeoutInMilliseconds(1000)
            ).andCommandPropertiesDefaults(
                HystrixCommandProperties.Setter()
                    .withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE)
            )
        );
        this.name = name;
    }

    @Override
    protected String run() throws Exception {
        try {
            logger.info("start run: " + Thread.currentThread().getId());
            return "Hello " + name + "!";
        } finally {
            logger.info("start end");
        }
    }

    @Override
    protected String getFallback() {
        try {
            logger.info("getFallback run: " + Thread.currentThread().getId());
            return "failed";
        } finally {
            logger.info("getFallback end");
        }
    }
}
