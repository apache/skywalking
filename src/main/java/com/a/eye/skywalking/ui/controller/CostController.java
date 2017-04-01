package com.a.eye.skywalking.ui.controller;

import com.a.eye.skywalking.ui.service.CostService;
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
public class CostController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(CostController.class);

    @Autowired
    private CostService service;

    @RequestMapping(value = "costDataLoad", method = RequestMethod.GET)
    @ResponseBody
    public void costDataLoad(@ModelAttribute("timeSliceType") String timeSliceType, @ModelAttribute("startTime") long startTime,
                             @ModelAttribute("endTime") long endTime, HttpServletResponse response) throws IOException {
        logger.debug("costDataLoad timeSliceType = %s, startTime = %s, endTime = %s", timeSliceType, startTime, endTime);
        JsonObject dagJson = service.loadCostData(timeSliceType, startTime, endTime);
        reply(dagJson.toString(), response);
    }
}
