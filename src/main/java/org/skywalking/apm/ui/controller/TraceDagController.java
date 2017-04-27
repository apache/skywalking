package org.skywalking.apm.ui.controller;

import org.skywalking.apm.ui.service.TraceDagService;
import org.skywalking.apm.ui.web.ControllerBase;
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
    public void dagNodesLoad(@ModelAttribute("timeSliceType") String timeSliceType, @ModelAttribute("startTime") long startTime,
                             @ModelAttribute("endTime") long endTime, HttpServletResponse response) throws IOException {
        logger.debug("dagNodesLoad timeSliceType = %s, startTime = %s, endTime = %s", timeSliceType, startTime, endTime);
        JsonObject dagJson = service.buildGraphData(timeSliceType, startTime, endTime);
        reply(dagJson.toString(), response);
    }
}
