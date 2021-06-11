/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.controller;

import org.apache.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileLogController {

    private static final Logger LOG4J_LOGGER = Logger.getLogger("fileLogger");
    private static final org.apache.logging.log4j.Logger LOG4J2_LOGGER = LogManager.getLogger("fileLogger");
    private static final org.slf4j.Logger LOGBACK_LOGGER = LoggerFactory.getLogger("fileLogger");

    @RequestMapping(value = "/file/logs/trigger")
    public String trigger() {
        LOG4J_LOGGER.info("log4j fileLogger ==> mills: " + System.currentTimeMillis());
        LOG4J2_LOGGER.info("log4j2 fileLogger ==> mills: " + System.currentTimeMillis());
        LOGBACK_LOGGER.info("logback fileLogger ==> mills: {}", System.currentTimeMillis());
        return TraceContext.traceId();
    }
}
