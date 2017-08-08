package org.skywalking.apm.collector.ui.jetty.handler;

import com.google.gson.JsonElement;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.SpanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SpanGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(SpanGetHandler.class);

    @Override public String pathSpec() {
        return "/span/spanId";
    }

    private SpanService service = new SpanService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String segmentId = req.getParameter("segmentId");
        String spanIdStr = req.getParameter("spanId");
        logger.debug("segmentSpanId: {}, spanIdStr: {}", segmentId, spanIdStr);

        int spanId;
        try {
            spanId = Integer.parseInt(spanIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("span id must be integer");
        }

        return service.load(segmentId, spanId);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
