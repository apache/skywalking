package org.skywalking.apm.collector.ui.jetty.handler.instancemetric;

import com.google.gson.JsonElement;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.InstanceJVMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class InstanceOsInfoGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(InstanceOsInfoGetHandler.class);

    @Override public String pathSpec() {
        return "/instance/os/instanceId";
    }

    private InstanceJVMService service = new InstanceJVMService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String instanceIdStr = req.getParameter("instanceId");
        logger.debug("instance os info get, instance id: {}", instanceIdStr);

        int instanceId;
        try {
            instanceId = Integer.parseInt(instanceIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("instance id must be integer");
        }

        return service.getInstanceOsInfo(instanceId);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
