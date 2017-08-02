package org.skywalking.apm.ui.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.service.SpanService;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
@RestController
public class SpanController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(SpanController.class);

    @Autowired
    private SpanService service;

    @GetMapping("spanDataLoad")
    public void spanDataLoad(@ModelAttribute("spanSegId") String spanSegId, HttpServletResponse response) throws IOException {
        logger.debug("costDataLoad spanSegId = %s", spanSegId);
        JsonObject dagJson = service.loadData(spanSegId);
        reply(dagJson.toString(), response);
    }
}
