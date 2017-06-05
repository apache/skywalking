package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.WorkerRef;

public abstract class AbstractPostWithHttpServlet extends AbstractHttpServlet {

    private Logger logger = LogManager.getFormatterLogger(AbstractPostWithHttpServlet.class);
    protected final WorkerRef ownerWorkerRef;

    AbstractPostWithHttpServlet(WorkerRef ownerWorkerRef) {
        this.ownerWorkerRef = ownerWorkerRef;
    }

    @Override final protected void doPost(HttpServletRequest request,
        HttpServletResponse response) throws ServletException, IOException {
        JsonObject resJson = new JsonObject();
        try {
            BufferedReader bufferedReader = request.getReader();
            doWork(bufferedReader, resJson);
            reply(response, resJson, HttpServletResponse.SC_OK);
        } catch (Exception e) {
            logger.error(e);
            resJson.addProperty("error", e.getMessage());
            reply(response, resJson, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    protected abstract void doWork(BufferedReader bufferedReader, JsonObject resJson) throws Exception;
}

