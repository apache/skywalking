/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

package org.skywalking.apm.ui.controller;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.service.ApplicationService;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author peng-yongsheng
 */
@RestController
public class ApplicationController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(HealthController.class);

    @Autowired
    private ApplicationService service;

    @GetMapping("/applications")
    public void load(@ModelAttribute("timeBucketType") String timeBucketType,
        @ModelAttribute("startTime") long startTime, @ModelAttribute("endTime") long endTime,
        HttpServletResponse response) throws IOException {

        logger.info("load applications, timeBucketType: %s, startTime: %s, endTime: %s", timeBucketType, startTime, endTime);
        reply(service.load(timeBucketType, startTime, endTime).toString(), response);
    }
}
