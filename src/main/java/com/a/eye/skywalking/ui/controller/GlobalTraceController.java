package com.a.eye.skywalking.ui.controller;

import com.a.eye.skywalking.ui.service.GlobalTraceService;
import com.a.eye.skywalking.ui.web.ControllerBase;
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
public class GlobalTraceController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(GlobalTraceController.class);

    @Autowired
    private GlobalTraceService service;

    @RequestMapping(value = "loadGlobalTraceData", method = RequestMethod.GET)
    @ResponseBody
    public void loadGlobalTraceData(@ModelAttribute("globalId") String globalId, HttpServletResponse response) throws IOException {
        logger.debug("costDataLoad globalId = %s", globalId);
        String globalTraceData = service.loadGlobalTraceData(globalId);
        reply(globalTraceData, response);
    }
}
