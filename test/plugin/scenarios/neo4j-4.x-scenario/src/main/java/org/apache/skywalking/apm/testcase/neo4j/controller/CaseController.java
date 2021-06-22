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

package org.apache.skywalking.apm.testcase.neo4j.controller;

import javax.annotation.Resource;
import org.apache.skywalking.apm.testcase.neo4j.service.TestCaseService;
import org.neo4j.driver.Driver;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
public class CaseController {

    private static final String SUCCESS = "Success";
    @Resource
    private TestCaseService testCaseService;
    @Resource
    private Driver driver;

    @RequestMapping("/neo4j-scenario")
    @ResponseBody
    public String testcase() throws Exception {
        testCaseService.sessionScenarioTest(driver);
        testCaseService.transactionScenarioTest(driver);
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() throws Exception {
        driver.verifyConnectivity();
        return SUCCESS;
    }

}
