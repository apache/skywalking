package org.skywalking.apm.ui.controller;

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

    @GetMapping("/health/instances")
    public void loadInstanceHealth(@ModelAttribute("timeBucket") long timeBucket,
        @RequestParam(value = "applicationIds[]", required = false) String[] applicationIds,
        HttpServletResponse response) throws IOException {

        logger.info("load Instance Health, timeBucket: %d, applicationIds: %s", timeBucket, applicationIds);
        reply(service.loadInstances(timeBucket, applicationIds).toString(), response);
    }
}
