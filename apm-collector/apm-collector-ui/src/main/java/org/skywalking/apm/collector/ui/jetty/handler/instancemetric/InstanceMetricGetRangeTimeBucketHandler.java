package org.skywalking.apm.collector.ui.jetty.handler.instancemetric;

import com.google.gson.JsonElement;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.InstanceJVMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class InstanceMetricGetRangeTimeBucketHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(InstanceMetricGetRangeTimeBucketHandler.class);

    @Override public String pathSpec() {
        return "/instance/jvm/instanceId/rangeBucket";
    }

    private InstanceJVMService service = new InstanceJVMService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String startTimeBucketStr = req.getParameter("startTimeBucket");
        String endTimeBucketStr = req.getParameter("endTimeBucket");
        String instanceIdStr = req.getParameter("instanceId");
        String[] metricTypes = req.getParameterValues("metricTypes");

        logger.debug("instance jvm metric get start timeBucket: {}, end timeBucket:{} , instance id: {}, metric types: {}", startTimeBucketStr, endTimeBucketStr, instanceIdStr, metricTypes);

        long startTimeBucket;
        try {
            startTimeBucket = Long.parseLong(startTimeBucketStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("start timeBucket must be long");
        }

        long endTimeBucket;
        try {
            endTimeBucket = Long.parseLong(endTimeBucketStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("end timeBucket must be long");
        }

        int instanceId;
        try {
            instanceId = Integer.parseInt(instanceIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("instance id must be integer");
        }

        if (metricTypes.length == 0) {
            throw new ArgumentsParseException("at least one metric type");
        }

        Set<String> metricTypeSet = new LinkedHashSet<>();
        for (String metricType : metricTypes) {
            metricTypeSet.add(metricType);
        }

        return service.getInstanceJvmMetrics(instanceId, metricTypeSet, startTimeBucket, endTimeBucket);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
