package org.skywalking.apm.ui.controller;

import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.service.TimeSyncService;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author pengys5
 */
@RestController
public class TimeSyncController extends ControllerBase {

    private Logger logger = LogManager.getFormatterLogger(TimeSyncController.class);

    @Autowired
    private TimeSyncService service;

    @GetMapping("time/sync/allInstance")
    public void allInstance(HttpServletResponse response) throws IOException {
        logger.debug("load all instance last time");
        long timeBucket = service.allInstance();
        JsonObject result = new JsonObject();
        result.addProperty("timeBucket", timeBucket);
        reply(result.toString(), response);
    }

    @GetMapping("time/sync/oneInstance")
    public void oneInstance(@ModelAttribute("instanceId") int instanceId,
        HttpServletResponse response) throws IOException {
        logger.debug("load one instance last time, instance id: %s", instanceId);
        long timeBucket = service.oneInstance(instanceId);
        JsonObject result = new JsonObject();
        result.addProperty("timeBucket", timeBucket);
        reply(result.toString(), response);
    }
}
