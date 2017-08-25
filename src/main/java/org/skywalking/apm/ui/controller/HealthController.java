package org.skywalking.apm.ui.controller;

import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.service.HealthService;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhangxin
 */
@RestController
public class HealthController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(HealthController.class);

    @Autowired
    private HealthService service;

    @GetMapping("syncTime")
    public void syncTimestamp(HttpServletResponse response) throws IOException {
        logger.info("syncTimestamp");

        long syncTimestamp = service.syncTimestamp();
        JsonObject result = new JsonObject();
        result.addProperty("timestamp", syncTimestamp);

        reply(result.toString(), response);
    }

    @GetMapping("/applications")
    public void loadApplications(@ModelAttribute("timestamp") long timestamp,
        HttpServletResponse response) throws IOException {
        logger.info("load applications[timestamps=%d]", timestamp);

        reply(service.loadApplications(timestamp).toString(), response);
    }

    @GetMapping("/health/instances")
    public void loadInstanceHealth(@RequestParam long timestamp,
        @RequestParam(value = "applicationIds[]", required = false) String[] applicationIds,
        HttpServletResponse response) throws IOException {

        logger.info("load Instance Health[timestamps=%d]", timestamp);
        reply(service.loadInstances(timestamp, applicationIds).toString(), response);
    }
}
