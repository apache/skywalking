package org.skywalking.apm.ui.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.service.CostService;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author pengys5
 */
@RestController
public class CostController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(CostController.class);

    @Autowired
    private CostService service;

    @GetMapping("costDataLoad")
    public void costDataLoad(@ModelAttribute("timeSliceType") String timeSliceType, @ModelAttribute("startTime") long startTime,
                             @ModelAttribute("endTime") long endTime, HttpServletResponse response) throws IOException {
        logger.debug("costDataLoad timeSliceType = %s, startTime = %s, endTime = %s", timeSliceType, startTime, endTime);
//        JsonObject dagJson = service.loadCostData(timeSliceType, startTime, endTime);
        reply("", response);
    }
}
