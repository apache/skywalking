package org.skywalking.apm.collector.ui.jetty.handler.servicetree;

import com.google.gson.JsonElement;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.ServiceTreeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceTreeGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(ServiceTreeGetHandler.class);

    @Override public String pathSpec() {
        return "/service/tree";
    }

    private ServiceTreeService service = new ServiceTreeService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        return null;
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
