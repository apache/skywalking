package org.skywalking.apm.collector.ui.jetty.handler.application;

import com.google.gson.JsonElement;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.ApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ApplicationsGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(ApplicationsGetHandler.class);

    @Override public String pathSpec() {
        return "/applications";
    }

    private ApplicationService service = new ApplicationService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        if (!req.getParameterMap().containsKey("startTime") || !req.getParameterMap().containsKey("endTime")) {
            throw new ArgumentsParseException("must contains startTime. endTime parameter");
        }

        String startTimeStr = req.getParameter("startTime");
        String endTimeStr = req.getParameter("endTime");
        logger.debug("applications get start time: {}, end time: {}", startTimeStr, endTimeStr);

        long startTime;
        try {
            startTime = Long.parseLong(startTimeStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("start time must be long");
        }

        long endTime;
        try {
            endTime = Long.parseLong(endTimeStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("end time must be long");
        }

        return service.getApplications(startTime, endTime);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
