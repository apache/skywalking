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

package org.apache.skywalking.apm.testcase.jsonrpc4j.controller;

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import com.googlecode.jsonrpc4j.ProxyUtil;
import org.apache.skywalking.apm.testcase.jsonrpc4j.services.DemoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.net.URL;

@RestController
@RequestMapping("/case")
public class CaseController {

    private static final String SUCCESS = "Success";

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return SUCCESS;
    }

    @RequestMapping("/json-rpc")
    @ResponseBody
    public String jsonRpc() throws MalformedURLException {
        JsonRpcHttpClient client = new JsonRpcHttpClient(
                new URL("http://localhost:8080/jsonrpc4j-1.x-scenario/path/to/demo-service"));
        DemoService demoService = ProxyUtil.createClientProxy(getClass().getClassLoader(), DemoService.class, client);
        demoService.sayHello();
        return SUCCESS;
    }
}
