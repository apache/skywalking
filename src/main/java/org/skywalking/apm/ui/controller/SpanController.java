package org.skywalking.apm.ui.controller;

import org.skywalking.apm.ui.web.ControllerBase;
import org.skywalking.apm.ui.service.SpanService;
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
public class SpanController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(SpanController.class);

    @Autowired
    private SpanService service;

    @RequestMapping(value = "spanDataLoad", method = RequestMethod.GET)
    @ResponseBody
    public void spanDataLoad(@ModelAttribute("spanSegId") String spanSegId, HttpServletResponse response) throws IOException {
        logger.debug("costDataLoad spanSegId = %s", spanSegId);
        JsonObject dagJson = service.loadData(spanSegId);
        reply(dagJson.toString(), response);
    }
}
