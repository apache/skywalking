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
public class ServiceTreeGetByIdHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(ServiceTreeGetByIdHandler.class);

    @Override public String pathSpec() {
        return "/service/tree/entryServiceId";
    }

    private ServiceTreeService service = new ServiceTreeService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        if (!req.getParameterMap().containsKey("entryServiceId") || !req.getParameterMap().containsKey("startTime") || !req.getParameterMap().containsKey("endTime")) {
            throw new ArgumentsParseException("must contains parameters: entryServiceId, startTime, endTime");
        }

        String entryServiceIdStr = req.getParameter("entryServiceId");
        String startTimeStr = req.getParameter("startTime");
        String endTimeStr = req.getParameter("endTime");
        logger.debug("service entry get entryServiceId: {}, startTime: {}, endTime: {}", entryServiceIdStr, startTimeStr, endTimeStr);

        int entryServiceId;
        try {
            entryServiceId = Integer.parseInt(entryServiceIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("entry service id must be integer");
        }

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

        return service.loadServiceTree(entryServiceId, startTime, endTime);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
