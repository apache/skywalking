package org.skywalking.apm.collector.ui.jetty.handler;

import com.google.gson.JsonElement;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.TraceStackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class TraceStackGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(TraceStackGetHandler.class);

    @Override public String pathSpec() {
        return "/traceStack/globalTraceId";
    }

    private TraceStackService service = new TraceStackService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String globalTraceId = req.getParameter("globalTraceId");
        logger.debug("globalTraceId: {}", globalTraceId);

        return service.load(globalTraceId);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
