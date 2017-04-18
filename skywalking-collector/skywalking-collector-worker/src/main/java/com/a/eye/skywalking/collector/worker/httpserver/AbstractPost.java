package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.*;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * @author pengys5
 */

public abstract class AbstractPost extends AbstractLocalAsyncWorker {

    private Logger logger = LogManager.getFormatterLogger(AbstractPost.class);

    public AbstractPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override final public void onWork(Object request) throws Exception {
        if (request instanceof String) {
            onReceive((String)request);
        } else {
            logger.error("unhandled request, request instance must String, but is %s", request.getClass().toString());
            saveException(new IllegalArgumentException("request instance must String"));
        }
    }

    protected abstract void onReceive(String reqJsonStr) throws Exception;

    static class PostWithHttpServlet extends AbstractHttpServlet {

        private final LocalAsyncWorkerRef ownerWorkerRef;

        PostWithHttpServlet(LocalAsyncWorkerRef ownerWorkerRef) {
            this.ownerWorkerRef = ownerWorkerRef;
        }

        @Override        final protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
            JsonObject resJson = new JsonObject();
            try {
                BufferedReader bufferedReader = request.getReader();
                StringBuilder dataStr = new StringBuilder();
                String tmpStr;
                while ((tmpStr = bufferedReader.readLine()) != null) {
                    dataStr.append(tmpStr);
                }
                ownerWorkerRef.tell(dataStr.toString());
                reply(response, resJson, HttpServletResponse.SC_OK);
            } catch (Exception e) {
                resJson.addProperty("error", e.getMessage());
                reply(response, resJson, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }
}
