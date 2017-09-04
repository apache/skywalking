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
public class EntryServiceGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(EntryServiceGetHandler.class);

    @Override public String pathSpec() {
        return "/service/entry";
    }

    private ServiceTreeService service = new ServiceTreeService();

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        if (!req.getParameterMap().containsKey("applicationId") || !req.getParameterMap().containsKey("entryServiceName")
            || !req.getParameterMap().containsKey("startTime") || !req.getParameterMap().containsKey("endTime")
            || !req.getParameterMap().containsKey("from") || !req.getParameterMap().containsKey("size")) {
            throw new ArgumentsParseException("must contains parameters: applicationId, entryServiceName, startTime, endTime, from, size");
        }

        String applicationIdStr = req.getParameter("applicationId");
        String entryServiceName = req.getParameter("entryServiceName");
        String startTimeStr = req.getParameter("startTime");
        String endTimeStr = req.getParameter("endTime");
        String fromStr = req.getParameter("from");
        String sizeStr = req.getParameter("size");
        logger.debug("service entry get applicationId: {}, entryServiceName: {}, startTime: {}, endTime: {}, from: {}, size: {}", applicationIdStr, entryServiceName, startTimeStr, endTimeStr, fromStr, sizeStr);

        int applicationId;
        try {
            applicationId = Integer.parseInt(applicationIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("application id must be integer");
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

        int from;
        try {
            from = Integer.parseInt(fromStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("from must be integer");
        }

        int size;
        try {
            size = Integer.parseInt(sizeStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("size must be integer");
        }

        return service.loadEntryService(applicationId, entryServiceName, startTime, endTime, from, size);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
