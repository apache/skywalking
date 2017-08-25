package org.skywalking.apm.ui.controller;

import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.service.TraceDagService;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

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
    public void dagNodesLoad(@ModelAttribute("startTime") long startTime,
        @ModelAttribute("endTime") long endTime, HttpServletResponse response) throws IOException {
        logger.debug("dagNodesLoad startTime = %s, endTime = %s", startTime, endTime);
        JsonObject dagJson = service.buildGraphData(startTime, endTime);
        reply(dagJson.toString(), response);
    }
}
