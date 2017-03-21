package com.a.eye.skywalking.ui.controller;

import com.a.eye.skywalking.ui.service.TraceDagService;
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
public class TraceDagController extends ControllerBase {
    private Logger logger = LogManager.getFormatterLogger(TraceDagController.class);

    @Autowired
    private TraceDagService service;

    @RequestMapping(value = "dagNodesLoad", method = RequestMethod.GET)
    @ResponseBody
    public void dagNodesLoad(@ModelAttribute("timeSliceType") String timeSliceType, @ModelAttribute("timeSliceValue") long timeSliceValue, HttpServletResponse response) throws IOException {
        logger.debug("dagNodesLoad timeSliceType = %s, timeSliceValue = %s", timeSliceType, timeSliceValue);
        JsonObject dagJson = service.buildGraphData(timeSliceType, timeSliceValue);
        reply(dagJson.toString(), response);
    }
}
