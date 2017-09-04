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
public class InstanceMetricGetOneTimeBucketHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(InstanceMetricGetOneTimeBucketHandler.class);

    @Override public String pathSpec() {
        return "/instance/jvm/instanceId/oneBucket";
    }

    private InstanceJVMService service = new InstanceJVMService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String timeBucketStr = req.getParameter("timeBucket");
        String instanceIdStr = req.getParameter("instanceId");
        String[] metricTypes = req.getParameterValues("metricTypes");

        logger.debug("instance jvm metric get timeBucket: {}, instance id: {}, metric types: {}", timeBucketStr, instanceIdStr, metricTypes);

        long timeBucket;
        try {
            timeBucket = Long.parseLong(timeBucketStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("timeBucket must be long");
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

        return service.getInstanceJvmMetric(instanceId, metricTypeSet, timeBucket);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
