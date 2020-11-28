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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/logback")
public class CaseController {

    private static final String SUCCESS = "Success";
    Logger logger = LoggerFactory.getLogger(CaseController.class);

    @RequestMapping("/no-param")
    @ResponseBody
    public String testcase() {
        logger.warn("no-param");
        return "no-param";
    }

    @RequestMapping("/one-param")
    @ResponseBody
    public String testParam() {
        logger.error("one param is {}", CaseController.class);
        return "one param";
    }
    @RequestMapping("/marker")
    @ResponseBody
    public String testMarker() {
        logger.warn(MarkerFactory.getMarker("TEST"), "test marker");
        return "test marker";
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return SUCCESS;
    }

}
