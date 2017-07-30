package org.skywalking.apm.collector.ui.jetty.handler;

import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String startTimeStr = req.getParameter("startTime");
        String endTimeStr = req.getParameter("endTime");
        String timeBucketType = req.getParameter("timeBucketType");
        logger.debug("startTime: {}, endTimeStr: {}, timeBucketType: {}", startTimeStr, endTimeStr, timeBucketType);

        long startTime = Long.valueOf(startTimeStr);
        long endTime = Long.valueOf(endTimeStr);
        JsonObject traceDagJson = service.load(startTime, endTime, timeBucketType);

        reply(resp, traceDagJson, HttpServletResponse.SC_OK);
    }
}
