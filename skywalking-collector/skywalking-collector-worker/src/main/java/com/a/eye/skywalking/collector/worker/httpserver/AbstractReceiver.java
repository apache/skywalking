package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.*;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author pengys5
 */
public abstract class AbstractReceiver extends AbstractLocalAsyncWorker {

    public AbstractReceiver(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    final public void onWork(Object request) throws Exception {
        if (request instanceof ReceiverMessage) {
            ReceiverMessage receiverMessage = (ReceiverMessage) request;
            onReceive(receiverMessage.request);
        }
    }

    protected abstract void onReceive(JsonObject request) throws Exception;

    static class ReceiveWithHttpServlet extends AbstractHttpServlet {

        private final LocalAsyncWorkerRef ownerWorkerRef;

        public ReceiveWithHttpServlet(LocalAsyncWorkerRef ownerWorkerRef) {
            this.ownerWorkerRef = ownerWorkerRef;
        }

        @Override
        final protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            JsonObject reqJson = new JsonObject();
            JsonObject resJson = new JsonObject();
            try {
                request.getParameter("json");
                ownerWorkerRef.tell(new ReceiverMessage(reqJson));
                reply(response, resJson, HttpServletResponse.SC_OK);
            } catch (Exception e) {
                resJson.addProperty("error", e.getMessage());
                reply(response, resJson, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    static class ReceiverMessage {
        private final JsonObject request;

        public ReceiverMessage(JsonObject request) {
            this.request = request;
        }
    }
}
