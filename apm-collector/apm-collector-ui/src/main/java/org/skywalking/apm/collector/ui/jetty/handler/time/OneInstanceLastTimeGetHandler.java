package org.skywalking.apm.collector.ui.jetty.handler.time;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.TimeSynchronousService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class OneInstanceLastTimeGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(OneInstanceLastTimeGetHandler.class);

    @Override public String pathSpec() {
        return "/time/oneInstance";
    }

    private TimeSynchronousService service = new TimeSynchronousService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String instanceIdStr = req.getParameter("instanceId");
        logger.debug("instanceId: {}", instanceIdStr);

        int instanceId;
        try {
            instanceId = Integer.parseInt(instanceIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("application instance id must be integer");
        }

        Long time = service.instanceLastTime(instanceId);
        logger.debug("application instance id: {}, instance last time: {}", instanceId, time);
        JsonObject timeJson = new JsonObject();
        timeJson.addProperty("timeBucket", time);
        return timeJson;
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
