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

package org.apache.skywalking.apm.testcase.jdkhttp.controller;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    private static final String SUCCESS = "Success";

    @RequestMapping("/jdk-http-scenario")
    @ResponseBody
    public String testcase() throws IOException {
        // Like gateway forward trace header.
        URL url = new URL("http://localhost:8080/jdk-http-scenario/case/receiveContext-0");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.addRequestProperty("key", "value");
        connection.addRequestProperty("sw8", "123456");
        int responseCode = connection.getResponseCode();
        return "Success:" + responseCode;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        // your codes
        return SUCCESS;
    }

    @RequestMapping("/receiveContext-0")
    @ResponseBody
    public String receiveContextService0() {
        return "receiveContext-0";
    }

}
