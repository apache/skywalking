/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.testcase.feign.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.feign.entity.User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/case")
public class CaseController {
    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    private RestRequest restRequest;

    public CaseController(RestRequest restRequest) {
        this.restRequest = restRequest;
    }

    @ResponseBody
    @RequestMapping("/healthCheck")
    public String healthcheck() {
        return "Success";
    }

    @ResponseBody
    @RequestMapping("/spring-cloud-feign-2.x-scenario")
    public String feignCase() {
        restRequest.createUser(new User(1, "test"));
        User user = restRequest.getById(1);
        LOGGER.info("find Id{} user. User name is {} ", user.getId(), user.getUserName());
        restRequest.updateUser(1, new User(0, "testA"));
        restRequest.deleteUser(1);
        return "success";
    }
}
