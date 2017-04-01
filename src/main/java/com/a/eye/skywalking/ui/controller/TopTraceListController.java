package com.a.eye.skywalking.ui.controller;

import com.a.eye.skywalking.ui.service.TopTraceListService;
import com.a.eye.skywalking.ui.web.ControllerBase;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author pengys5
 */
@Controller
public class TopTraceListController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(TopTraceListController.class);

    @Autowired
    private TopTraceListService service;

    @RequestMapping(value = "topTraceListDataLoad", method = RequestMethod.GET)
    @ResponseBody
    public void topTraceListDataLoad(@ModelAttribute("startTime") long startTime, @ModelAttribute("endTime") long endTime,
                                     @ModelAttribute("limit") int limit, @ModelAttribute("from") int from, @ModelAttribute("minCost") int minCost,
                                     @ModelAttribute("maxCost") int maxCost, @ModelAttribute("globalTraceId") String globalTraceId,
                                     HttpServletResponse response) throws IOException {
        logger.debug("topTraceListDataLoad startTime = %s, endTime = %s, from=%s, minCost=%s, maxCost=%s, globalTraceId=%s", startTime, endTime, from, minCost, maxCost, globalTraceId);
        JsonObject topSegJson = service.topTraceListDataLoad(startTime, endTime, minCost, maxCost, limit, from, globalTraceId);
        reply(topSegJson.toString(), response);
    }
}
