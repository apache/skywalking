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

package org.apache.skywalking.apm.testcase.asynchttpclient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/asynchttpclient")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    @GetMapping("/back")
    @ResponseBody
    public String back() {
        return "Hello back";
    }

    @GetMapping("/case")
    @ResponseBody
    public String asynchttpclientScenario() throws Exception {
        String content = asyncRequest("http://localhost:8080/asynchttpclient/back");
        return content;
    }

    @RequestMapping(value = "/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return "Success";
    }

    public static final String asyncRequest(String url) throws Exception {

        Request request = new RequestBuilder().setUrl("http://localhost:8080/asynchttpclient/back").build();

        AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient();

        try {
            ListenableFuture<Response> response = asyncHttpClient.executeRequest(request);
        } catch (Exception e) {
            LOGGER.error("AsyncHttpClient executeRequest failed" + e);
        }

        return "Success";
    }
}
