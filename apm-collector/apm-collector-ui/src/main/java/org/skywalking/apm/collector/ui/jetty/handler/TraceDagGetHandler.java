package org.skywalking.apm.collector.ui.jetty.handler;

import com.google.gson.JsonElement;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.TraceDagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class TraceDagGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(TraceDagGetHandler.class);

    @Override public String pathSpec() {
        return "/traceDag";
    }

    private TraceDagService service = new TraceDagService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String startTimeStr = req.getParameter("startTime");
        String endTimeStr = req.getParameter("endTime");
        String timeBucketType = req.getParameter("timeBucketType");
        logger.debug("startTime: {}, endTimeStr: {}, timeBucketType: {}", startTimeStr, endTimeStr, timeBucketType);

        long startTime = Long.valueOf(startTimeStr);
        long endTime = Long.valueOf(endTimeStr);
        return service.load(startTime, endTime, timeBucketType);
    }

    @Override protected void doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
