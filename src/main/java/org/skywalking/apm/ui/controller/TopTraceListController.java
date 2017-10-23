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
import org.skywalking.apm.ui.service.TopTraceListService;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author peng-yongsheng
 */
@Controller
public class TopTraceListController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(TopTraceListController.class);

    @Autowired
    private TopTraceListService service;

    @RequestMapping(value = "topTraceListDataLoad", method = RequestMethod.GET)
    @ResponseBody
    public void topTraceListDataLoad(@ModelAttribute("startTime") long startTime,
        @ModelAttribute("endTime") long endTime,
        @ModelAttribute("limit") int limit, @ModelAttribute("from") int from, @ModelAttribute("minCost") int minCost,
        @ModelAttribute("maxCost") int maxCost, @ModelAttribute("globalTraceId") String globalTraceId,
        @ModelAttribute("applicationId") int applicationId, @ModelAttribute("error") String error,
        @ModelAttribute("operationName") String operationName, @ModelAttribute("sort") String sort,
        HttpServletResponse response) throws IOException {
        logger.debug("topTraceListDataLoad startTime = %s, endTime = %s, from=%s, minCost=%s, maxCost=%s, globalTraceId=%s, operationName=%s, applicationId=%s, error=%s, sort=%s", startTime, endTime, from, minCost, maxCost, globalTraceId, operationName, applicationId, error, sort);
        JsonObject topSegJson = service.topTraceListDataLoad(startTime, endTime, minCost, maxCost, limit, from, globalTraceId, operationName, applicationId, error, sort);
        reply(topSegJson.toString(), response);
    }
}
