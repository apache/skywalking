package org.skywalking.apm.collector.ui.jetty.handler.instancehealth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.InstanceHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class InstanceHealthGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(InstanceHealthGetHandler.class);

    @Override public String pathSpec() {
        return "/instance/health/applicationId";
    }

    private InstanceHealthService service = new InstanceHealthService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String timeBucketStr = req.getParameter("timeBucket");
        String[] applicationIdsStr = req.getParameterValues("applicationIds");
        logger.debug("instance health get timeBucket: {}, applicationIdsStr: {}", timeBucketStr, applicationIdsStr);

        long timeBucket;
        try {
            timeBucket = Long.parseLong(timeBucketStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("timestamp must be long");
        }

        int[] applicationIds = new int[applicationIdsStr.length];
        for (int i = 0; i < applicationIdsStr.length; i++) {
            try {
                applicationIds[i] = Integer.parseInt(applicationIdsStr[i]);
            } catch (NumberFormatException e) {
                throw new ArgumentsParseException("application id must be integer");
            }
        }

        JsonObject response = new JsonObject();
        response.addProperty("timeBucket", timeBucket);
        JsonArray appInstances = new JsonArray();
        response.add("appInstances", appInstances);

        for (int applicationId : applicationIds) {
            appInstances.add(service.getInstances(timeBucket, applicationId));
        }
        return response;
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
