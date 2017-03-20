package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.*;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * @author pengys5
 */
public abstract class AbstractSearcher extends AbstractLocalSyncWorker {

    public AbstractSearcher(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    final public void onWork(Object request, Object response) throws Exception {
        Map<String, String[]> parameterMap = (Map<String, String[]>) request;
        onSearch(parameterMap, (JsonObject) response);
    }

    protected abstract void onSearch(Map<String, String[]> request, JsonObject response) throws Exception;

    static class SearchWithHttpServlet extends AbstractHttpServlet {

        private final LocalSyncWorkerRef ownerWorkerRef;

        SearchWithHttpServlet(LocalSyncWorkerRef ownerWorkerRef) {
            this.ownerWorkerRef = ownerWorkerRef;
        }

        @Override
        final protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            Map<String, String[]> parameterMap = request.getParameterMap();

            JsonObject resJson = new JsonObject();
            try {
                ownerWorkerRef.ask(parameterMap, resJson);
                reply(response, resJson, HttpServletResponse.SC_OK);
            } catch (Exception e) {
                resJson.addProperty("error", e.getMessage());
                reply(response, resJson, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }
}
