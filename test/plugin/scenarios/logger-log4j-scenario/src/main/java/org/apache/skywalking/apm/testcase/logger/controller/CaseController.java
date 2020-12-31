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

package org.apache.skywalking.apm.testcase.logger.controller;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.skywalking.apm.testcase.logger.utils.HttpUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/log4j")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);
    private static final String SUCCESS = "Success";
    @Value("${logger.host:localhost:8080}")
    private String loggerAddress;

    @RequestMapping("/no-param")
    @ResponseBody
    public String noParam() {
        LOGGER.error("no-param");
        return "no-param";
    }

    @RequestMapping("/one-param")
    @ResponseBody
    public String oneParam() {
        LOGGER.error("throwable-param", new Exception());
        return "one param";
    }

    @RequestMapping("/testcase")
    public String testcase() {
        HttpUtils.visit("http://" + loggerAddress + "/log4j/no-param");
        HttpUtils.visit("http://" + loggerAddress + "/log4j/one-param");
        return "test";
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return SUCCESS;
    }
}
