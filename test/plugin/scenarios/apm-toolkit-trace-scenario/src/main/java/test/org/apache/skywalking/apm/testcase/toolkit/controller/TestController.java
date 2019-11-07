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

package test.org.apache.skywalking.apm.testcase.toolkit.controller;

import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * @author caoyixiong
 */
@RestController
@RequestMapping("/case")
public class TestController {

    private static final String SUCCESS = "Success";

    @Autowired
    private TestService testService;

    @RequestMapping("/tool-kit")
    public String toolKitCase() {
        testService.testTag();
        testService.testInfo();
        testService.testDebug();
        testService.testError();
        testService.testErrorMsg();
        testService.testErrorThrowable();
        testService.asyncCallable(() -> {
            visit("http://localhost:8080/apm-toolkit-trace-scenario/case/asyncVisit/callable");
            return true;
        });
        testService.asyncRunnable(() -> {
            try {
                visit("http://localhost:8080/apm-toolkit-trace-scenario/case/asyncVisit/runnable");
            } catch (IOException e) {
                // ignore
            }
        });
        testService.asyncSupplier(()->{
            try {
                visit("http://localhost:8080/apm-toolkit-trace-scenario/case/asyncVisit/supplier");
            } catch (IOException e) {
                // ignore
            }
            return true;
        });
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    public String healthCheck() {
        return SUCCESS;
    }

    @RequestMapping("/asyncVisit/runnable")
    public String asyncVisitRunnable() {
        return SUCCESS;
    }

    @RequestMapping("/asyncVisit/callable")
    public String asyncVisitCallable() {
        return SUCCESS;
    }

    @RequestMapping("/asyncVisit/supplier")
    public String asyncVisitSupplier() {
    	return SUCCESS;
    }


    private static void visit(String url) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet httpget = new HttpGet(url);
            ResponseHandler<String> responseHandler = response -> {
                HttpEntity entity = response.getEntity();
                return entity != null ? EntityUtils.toString(entity) : null;
            };
            httpclient.execute(httpget, responseHandler);
        } finally {
            httpclient.close();
        }
    }
}
