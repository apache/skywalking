package org.skywalking.apm.collector.agentstream.jetty.handler;

import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class TraceSegmentServiceHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(TraceSegmentServiceHandler.class);

    @Override public String pathSpec() {
        return null;
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override protected void doPost(HttpServletRequest req) throws ArgumentsParseException {
        try {
            BufferedReader reader = req.getReader();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
