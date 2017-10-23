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
import org.skywalking.apm.ui.service.ServiceTreeService;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author peng-yongsheng
 */
@RestController
public class ServiceTreeController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(ServiceTreeController.class);

    @Autowired
    private ServiceTreeService service;

    @GetMapping("/service/tree/entryServiceId")
    public void load(@ModelAttribute("entryServiceId") int entryServiceId,
        @ModelAttribute("timeBucketType") String timeBucketType,
        @ModelAttribute("startTime") long startTime, @ModelAttribute("endTime") long endTime,
        HttpServletResponse response) throws IOException {

        logger.info("load service tree, entryServiceId: %s, timeBucketType: %s, startTime: %s, endTime: %s", entryServiceId, timeBucketType, startTime, endTime);
        reply(service.load(entryServiceId, startTime, endTime).toString(), response);
    }

    @GetMapping("/service/tree/entryServiceName")
    public void load(@ModelAttribute("entryApplicationId") int entryApplicationId,
        @ModelAttribute("entryServiceName") String entryServiceName,
        @ModelAttribute("timeBucketType") String timeBucketType,
        @ModelAttribute("startTime") long startTime, @ModelAttribute("endTime") long endTime,
        HttpServletResponse response) throws IOException {

        logger.info("load service tree, entryApplicationId: %s, entryServiceName: %s, timeBucketType: %s, startTime: %s, endTime: %s", entryApplicationId, entryServiceName, timeBucketType, startTime, endTime);
        reply(service.load(entryApplicationId, entryServiceName, startTime, endTime).toString(), response);
    }
}
