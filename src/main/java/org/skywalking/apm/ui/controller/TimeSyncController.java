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

import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.service.TimeSyncService;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author peng-yongsheng
 */
@RestController
public class TimeSyncController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(TimeSyncController.class);

    @Autowired
    private TimeSyncService service;

    @GetMapping("time/sync/allInstance")
    public void allInstance(HttpServletResponse response) throws IOException {
        logger.debug("load all instance last time");
        long timeBucket = service.allInstance();
        JsonObject result = new JsonObject();
        result.addProperty("timeBucket", timeBucket);
        reply(result.toString(), response);
    }

    @GetMapping("time/sync/oneInstance")
    public void oneInstance(@ModelAttribute("instanceId") int instanceId,
        HttpServletResponse response) throws IOException {
        logger.debug("load one instance last time, instance id: %s", instanceId);
        long timeBucket = service.oneInstance(instanceId);
        JsonObject result = new JsonObject();
        result.addProperty("timeBucket", timeBucket);
        reply(result.toString(), response);
    }
}
