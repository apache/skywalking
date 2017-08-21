package org.skywalking.apm.ui.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Calendar;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.web.ControllerBase;
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

    @GetMapping("syncTime")
    public void syncTimestamp(HttpServletResponse response) throws IOException {
        logger.info("syncTimestamp");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -10);
        JsonObject result = new JsonObject();
        result.addProperty("timestamp", calendar.getTimeInMillis());

        reply(result.toString(), response);
    }

    @GetMapping("/applications")
    public void loadApplications(@ModelAttribute("timestamp") long timestamp,
        HttpServletResponse response) throws IOException {
        logger.info("load applications[timestamps=%d]", timestamp);

        JsonObject applications = new Gson().fromJson("{\"applications\":[{\"applicationName\":\"Account application\",\"applicationId\":1,\"instanceCount\":5},{\"applicationName\":\"Biling application\",\"applicationId\":2,\"instanceCount\":2},{\"applicationName\":\"Commons application\",\"applicationId\":3,\"instanceCount\":1},{\"applicationName\":\"Order application\",\"applicationId\":4,\"instanceCount\":12},{\"applicationName\":\"Test application\",\"applicationId\":5,\"instanceCount\":1}]}", JsonObject.class);

        reply(applications.toString(), response);
    }

    @GetMapping("/health/instances")
    public void loadInstanceHealth(@RequestParam long timestamp,
        @RequestParam(value = "applicationIds[]", required = false) String[] applicationIds,
        HttpServletResponse response) throws IOException {

        logger.info("load Instance Health[timestamps=%d]", timestamp);
        // 动态效果，
        if ((timestamp / 1000) % 5 == 0) {
            JsonObject instances = new Gson().fromJson("{\"appInstances\":[{\"applicationCode\":\"Account Application\",\"applicationId\":1,\"instances\":[{\"id\":1,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":0,\"status\":0},{\"id\":29,\"tps\":700,\"avg\":5000,\"ygc\":10,\"ogc\":1,\"healthLevel\":1,\"status\":1},{\"id\":30,\"tps\":600,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":0,\"status\":1},{\"id\":41,\"tps\":100,\"avg\":5000,\"ygc\":7,\"ogc\":3,\"healthLevel\":2,\"status\":0},{\"id\":58,\"tps\":800,\"avg\":5000,\"ygc\":2,\"ogc\":0,\"healthLevel\":3,\"status\":0}]},{\"applicationCode\":\"Biling Application\",\"applicationId\":2,\"instances\":[{\"id\":61,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":3,\"status\":0},{\"id\":72,\"tps\":700,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":3,\"status\":1}]},{\"applicationCode\":\"Order Application\",\"applicationId\":3,\"instances\":[{\"id\":84,\"tps\":500,\"avg\":5000,\"ygc\":12,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":92,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":1,\"status\":1},{\"id\":110,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":101,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":0,\"status\":0},{\"id\":122,\"tps\":500,\"avg\":5000,\"ygc\":2,\"ogc\":0,\"healthLevel\":1,\"status\":0},{\"id\":163,\"tps\":500,\"avg\":5000,\"ygc\":3,\"ogc\":0,\"healthLevel\":0,\"status\":0},{\"id\":174,\"tps\":500,\"avg\":5000,\"ygc\":1,\"ogc\":1,\"healthLevel\":2,\"status\":0},{\"id\":152,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":3,\"status\":0},{\"id\":119,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":261,\"tps\":500,\"avg\":5000,\"ygc\":0,\"ogc\":0,\"healthLevel\":3,\"status\":0},{\"id\":222,\"tps\":500,\"avg\":5000,\"ygc\":4,\"ogc\":0,\"healthLevel\":1,\"status\":0},{\"id\":264,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":252,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":1},{\"id\":627,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":617,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":657,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6597,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6157,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":1657,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":61257,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":1},{\"id\":61457,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":65637,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":61557,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6581,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":3657,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":65347,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6457,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6257,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":1},{\"id\":6527,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":42657,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":65257,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0}]},{\"applicationCode\":\"Test Application\",\"applicationId\":4,\"instances\":[{\"id\":226,\"tps\":500,\"avg\":5000,\"ygc\":4,\"ogc\":1,\"healthLevel\":0,\"status\":0}]}]}", JsonObject.class);
            reply(instances.toString(), response);
        } else {
            JsonObject instances = new Gson().fromJson("{\"appInstances\":[{\"applicationCode\":\"Account Application\",\"applicationId\":1,\"instances\":[{\"id\":1,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":0,\"status\":0},{\"id\":29,\"tps\":700,\"avg\":5000,\"ygc\":10,\"ogc\":1,\"healthLevel\":1,\"status\":0},{\"id\":30,\"tps\":600,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":0,\"status\":0},{\"id\":41,\"tps\":100,\"avg\":5000,\"ygc\":7,\"ogc\":3,\"healthLevel\":2,\"status\":0},{\"id\":58,\"tps\":800,\"avg\":5000,\"ygc\":2,\"ogc\":0,\"healthLevel\":3,\"status\":0}]},{\"applicationCode\":\"Biling Application\",\"applicationId\":2,\"instances\":[{\"id\":61,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":3,\"status\":0},{\"id\":72,\"tps\":700,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":3,\"status\":0}]},{\"applicationCode\":\"Order Application\",\"applicationId\":3,\"instances\":[{\"id\":84,\"tps\":500,\"avg\":5000,\"ygc\":12,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":92,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":1,\"status\":0},{\"id\":110,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":101,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":0,\"status\":0},{\"id\":122,\"tps\":500,\"avg\":5000,\"ygc\":2,\"ogc\":0,\"healthLevel\":1,\"status\":0},{\"id\":163,\"tps\":500,\"avg\":5000,\"ygc\":3,\"ogc\":0,\"healthLevel\":0,\"status\":0},{\"id\":174,\"tps\":500,\"avg\":5000,\"ygc\":1,\"ogc\":1,\"healthLevel\":2,\"status\":0},{\"id\":152,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":3,\"status\":0},{\"id\":119,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":261,\"tps\":500,\"avg\":5000,\"ygc\":0,\"ogc\":0,\"healthLevel\":3,\"status\":0},{\"id\":222,\"tps\":500,\"avg\":5000,\"ygc\":4,\"ogc\":0,\"healthLevel\":1,\"status\":0},{\"id\":264,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":252,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":627,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":617,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":657,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6597,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6157,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":1657,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":61257,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":61457,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":65637,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":61557,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6581,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":3657,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":65347,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6457,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6257,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":6527,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":42657,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0},{\"id\":65257,\"tps\":500,\"avg\":5000,\"ygc\":10,\"ogc\":0,\"healthLevel\":2,\"status\":0}]},{\"applicationCode\":\"Test Application\",\"applicationId\":4,\"instances\":[{\"id\":226,\"tps\":500,\"avg\":5000,\"ygc\":4,\"ogc\":1,\"healthLevel\":0,\"status\":0}]}]}", JsonObject.class);
            reply(instances.toString(), response);
        }

    }
}
