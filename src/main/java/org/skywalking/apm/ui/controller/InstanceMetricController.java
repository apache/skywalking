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

        if (startTime == endTime) {
            // 调用一个时间点的数据
        } else {
            // 调用不同点的数据
        }
        JsonObject result;
        if (count % 10 == 0) {
            result = new Gson().fromJson("{\"cpu\":[0],\"gc\":{\"ygc\":[1],\"ogc\":[0]},\"heapMemory\":{\"max\":2048,\"min\":1024,\"used\":[0]},\"tps\":[653],\"respTime\":[4589]}", JsonObject.class);
        } else {
            result = new Gson().fromJson("{\"cpu\":[52],\"gc\":{\"ygc\":[1],\"ogc\":[0]},\"heapMemory\":{\"max\":2048,\"min\":1024,\"used\":[1238]},\"tps\":[653],\"respTime\":[4589]}", JsonObject.class);
        }
        count++;
        reply(result.toString(), response);
    }
}
