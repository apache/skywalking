package org.skywalking.apm.ui.controller;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.service.TraceStackService;
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
public class TraceStackController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(TraceStackController.class);

    @Autowired
    private TraceStackService service;

    @RequestMapping(value = "loadTraceStackData", method = RequestMethod.GET)
    @ResponseBody
    public void loadTraceStackData(@ModelAttribute("globalId") String globalId,
        HttpServletResponse response) throws IOException {
        logger.debug("costDataLoad globalId = %s", globalId);
        String globalTraceData = service.loadTraceStackData(globalId);
        reply(globalTraceData, response);
    }
}
