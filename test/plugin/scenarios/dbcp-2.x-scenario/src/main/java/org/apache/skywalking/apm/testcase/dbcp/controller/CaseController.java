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

package org.apache.skywalking.apm.testcase.dbcp.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.dbcp.service.CaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
public class CaseController {

    @Autowired
    CaseService caseService;

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    private static final String SUCCESS = "Success";
    
    @RequestMapping("/dbcp-2.x-scenario")
    @ResponseBody
    public String testcase() throws Exception {
        try {
            caseService.testCase();
        } catch (Exception e) {
            LOGGER.error("Failed to execute sql.", e);
            throw e;
        }
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() throws Exception {
        return SUCCESS;
    }

}
