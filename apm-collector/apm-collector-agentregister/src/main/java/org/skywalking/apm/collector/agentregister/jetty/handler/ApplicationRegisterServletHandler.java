package org.skywalking.apm.collector.agentregister.jetty.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.agentregister.application.ApplicationIDService;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ApplicationRegisterServletHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(ApplicationRegisterServletHandler.class);

    private ApplicationIDService applicationIDService = new ApplicationIDService();
    private Gson gson = new Gson();
    private static final String APPLICATION_CODE = "c";
    private static final String APPLICATION_ID = "i";

    @Override public String pathSpec() {
        return "/application/register";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        JsonArray responseArray = new JsonArray();
        try {
            JsonArray applicationCodes = gson.fromJson(req.getReader(), JsonArray.class);
            for (int i = 0; i < applicationCodes.size(); i++) {
                String applicationCode = applicationCodes.get(i).getAsString();
                int applicationId = applicationIDService.getOrCreate(applicationCode);
                JsonObject mapping = new JsonObject();
                mapping.addProperty(APPLICATION_CODE, applicationCode);
                mapping.addProperty(APPLICATION_ID, applicationId);
                responseArray.add(mapping);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return responseArray;
    }
}
