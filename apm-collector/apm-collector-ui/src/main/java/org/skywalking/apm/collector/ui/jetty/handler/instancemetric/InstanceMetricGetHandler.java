package org.skywalking.apm.collector.ui.jetty.handler.instancemetric;

import com.google.gson.JsonElement;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.InstanceHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class InstanceMetricGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(InstanceMetricGetHandler.class);

    @Override public String pathSpec() {
        return "/instance/jvm/instanceId";
    }

    private InstanceHealthService service = new InstanceHealthService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String timestampStr = req.getParameter("timestamp");
        String applicationIdStr = req.getParameter("applicationId");
        logger.debug("instance health get timestamp: {}", timestampStr);

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("timestamp must be long");
        }

        int applicationId;
        try {
            applicationId = Integer.parseInt(applicationIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("application id must be integer");
        }

        return service.getInstances(timestamp, applicationId);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
