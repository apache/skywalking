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

package org.apache.skywalking.apm.testcase.elasticsearch.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.elasticsearch.RestHighLevelClientCase;
import org.apache.skywalking.apm.testcase.elasticsearch.TransportClientCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/elasticsearch-case/case")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    @Autowired
    private RestHighLevelClientCase restHighLevelClientCase;

    @Autowired
    private TransportClientCase transportClientCase;

    @GetMapping("/healthCheck")
    public String healthcheck() throws Exception {
        restHighLevelClientCase.healthCheck();
        return "Success";
    }

    @GetMapping("/elasticsearch")
    public String elasticsearch() throws Exception {
        restHighLevelClientCase.elasticsearch();
        transportClientCase.elasticsearch();

        return "Success";
    }
}

