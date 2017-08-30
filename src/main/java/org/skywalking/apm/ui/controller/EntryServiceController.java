package org.skywalking.apm.ui.controller;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.service.EntryServiceService;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author pengys5
 */
@RestController
public class EntryServiceController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(EntryServiceController.class);

    @Autowired
    private EntryServiceService service;

    @GetMapping("/service/entry/entryServiceList")
    public void load(@ModelAttribute("applicationId") int applicationId,
        @ModelAttribute("timeBucketType") String timeBucketType,
        @ModelAttribute("entryServiceName") String entryServiceName,
        @ModelAttribute("startTime") long startTime, @ModelAttribute("endTime") long endTime,
        @ModelAttribute("from") int from, @ModelAttribute("size") int size,
        HttpServletResponse response) throws IOException {

        logger.info("load entry service list, applicationId: %s, entryServiceName: %s, timeBucketType: %s, startTime: %s, endTime: %s, from: %s, size: %s", applicationId, entryServiceName, timeBucketType, startTime, endTime, from, size);
        reply(service.load(applicationId, entryServiceName, startTime, endTime, from, size).toString(), response);
    }
}
