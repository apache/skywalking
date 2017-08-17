package org.skywalking.apm.collector.ui.jetty.handler.instancehealth;

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
public class ApplicationsGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(ApplicationsGetHandler.class);

    @Override public String pathSpec() {
        return "/instance/health/applications";
    }

    private InstanceHealthService service = new InstanceHealthService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String timestamp = req.getParameter("timestamp");
        logger.debug("instance health applications get time: {}", timestamp);

        long time;
        try {
            time = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("time must be long");
        }

        return service.getApplications(time);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
