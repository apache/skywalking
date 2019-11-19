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

package test.apache.skywalking.apm.testcase.hystrix.controller;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/case")
public class CaseController {

    private static final Logger logger = LogManager.getLogger(CaseController.class);

    private static final String SUCCESS = "Success";

    @PostConstruct
    public void setUp() {
        HystrixPlugins.getInstance().registerCommandExecutionHook(new HystrixCommandExecutionHook() {
            @Override public <T> void onStart(HystrixInvokable<T> commandInstance) {
                logger.info("[hookA] onStart: " + Thread.currentThread().getId());
                super.onStart(commandInstance);
            }

            @Override public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
                logger.info("[hookA] onExecutionStart: " + Thread.currentThread().getId());
                super.onExecutionStart(commandInstance);
            }

            @Override public <T> void onExecutionSuccess(HystrixInvokable<T> commandInstance) {
                logger.info("[hookA] onExecutionSuccess: " + Thread.currentThread().getId());
                super.onExecutionSuccess(commandInstance);
            }

            @Override public <T> Exception onExecutionError(HystrixInvokable<T> commandInstance, Exception e) {
                logger.info("[hookA] onExecutionError: " + Thread.currentThread().getId());
                return super.onExecutionError(commandInstance, e);
            }

            @Override public <T> Exception onRunError(HystrixCommand<T> commandInstance, Exception e) {
                logger.info("[hookA] onRunError: " + Thread.currentThread().getId());
                return super.onRunError(commandInstance, e);
            }
        });
    }

    @RequestMapping("/hystrix-scenario")
    @ResponseBody
    public String testcase() throws InterruptedException, ExecutionException {
        List<Future<String>> fs = new ArrayList<Future<String>>();
        fs.add(new TestBCommand("World").queue());
        logger.info(new TestACommand("World").execute());
        for (Future<String> f : fs) {
            logger.info(f.get());
        }
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return SUCCESS;
    }

}
