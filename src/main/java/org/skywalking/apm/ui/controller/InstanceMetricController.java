package org.skywalking.apm.ui.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.ui.web.ControllerBase;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InstanceMetricController extends ControllerBase {

    private static int count = 0;
    private Logger logger = LogManager.getFormatterLogger(InstanceMetricController.class);

    @RequestMapping("/instanceInfo")
    public void instanceInfo(@RequestParam("instanceId") int instanceId,
        HttpServletResponse response) throws IOException {
        logger.info("load instance info : %d", instanceId);
        JsonObject result = new Gson().fromJson("{\"osName\":\"Linux\",\"hostName\":\"ascrutae\",\"processId\":3753,\"ipv4s\":[\"192.168.0.1\",\"10.0.0.1\",\"223.56.23.64\"]}", JsonObject.class);
        reply(result.toString(), response);
    }

    @RequestMapping("/metricInfoWithTimeRange")
    public void metricInfoWithTimeRange(@RequestParam("instanceId") int instanceId,
        @RequestParam("metricNames[]") String metricNames,
        @RequestParam("startTime") long startTime,
        @RequestParam("endTime") long endTime,
        HttpServletResponse response) throws IOException {
        logger.info("load metric Info: %d, %d, %d, %s", instanceId, startTime, endTime, metricNames);



        JsonObject result;
        result = new Gson().fromJson("{\"cpu\":[{\"timeBucket\":20170827092223,\"data\":25},{\"timeBucket\":20170827092224,\"data\":25},{\"timeBucket\":20170827092226,\"data\":25},{\"timeBucket\":20170827092227,\"data\":25}],\"heapMemory\":[{\"timeBucket\":20170827092223,\"data\":{\"init\":1024,\"max\":2048,\"used\":1906}},{\"timeBucket\":20170827092224,\"data\":{\"init\":1024,\"max\":2048,\"used\":1503}},{\"timeBucket\":20170827092226,\"data\":{\"init\":1024,\"max\":2048,\"used\":1708}},{\"timeBucket\":20170827092227,\"data\":{\"init\":1024,\"max\":2048,\"used\":1046}}],\"respTime\":[{\"timeBucket\":20170827092223,\"data\":350},{\"timeBucket\":20170827092224,\"data\":550},{\"timeBucket\":20170827092226,\"data\":600},{\"timeBucket\":20170827092227,\"data\":570}],\"tps\":[{\"timeBucket\":20170827092223,\"data\":200},{\"timeBucket\":20170827092224,\"data\":210},{\"timeBucket\":20170827092226,\"data\":220},{\"timeBucket\":20170827092227,\"data\":219}],\"gc\":[{\"timeBucket\":20170827092223,\"data\":{\"ygc\":1,\"ogc\":0}},{\"timeBucket\":20170827092224,\"data\":{\"ygc\":2,\"ogc\":1}},{\"timeBucket\":20170827092226,\"data\":{\"ygc\":0,\"ogc\":0}},{\"timeBucket\":20170827092227,\"data\":{\"ygc\":3,\"ogc\":2}}]}", JsonObject.class);
        reply(result.toString(), response);
    }
}
