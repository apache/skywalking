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

    @RequestMapping(value = "initDagNodes", method = RequestMethod.GET)
    @ResponseBody
    public void initDagNodes(@ModelAttribute("query") String query, HttpServletResponse response) throws IOException {
        logger.debug("initDagNodes");
        JsonObject dagJson = service.getDag();
        reply(dagJson.toString(), response);
    }
}
