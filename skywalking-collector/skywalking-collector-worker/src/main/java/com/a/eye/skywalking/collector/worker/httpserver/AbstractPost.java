package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * @author pengys5
 */

public abstract class AbstractPost extends AbstractLocalAsyncWorker {

    public AbstractPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    final public void onWork(Object message) throws Exception {
        onReceive(message);
    }

    protected abstract void onReceive(Object message) throws Exception;

    static class PostWithHttpServlet extends AbstractHttpServlet {

        private final LocalAsyncWorkerRef ownerWorkerRef;

        PostWithHttpServlet(LocalAsyncWorkerRef ownerWorkerRef) {
            this.ownerWorkerRef = ownerWorkerRef;
        }

        @Override
        final protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            JsonObject resJson = new JsonObject();
            try {
                BufferedReader bufferedReader = request.getReader();
                streamReader(bufferedReader);
                reply(response, resJson, HttpServletResponse.SC_OK);
            } catch (Exception e) {
                e.printStackTrace();
                resJson.addProperty("error", e.getMessage());
                reply(response, resJson, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        private void streamReader(BufferedReader bufferedReader) throws Exception {
            try (JsonReader reader = new JsonReader(bufferedReader)) {
                readSegmentArray(reader);
            }
        }

        private void readSegmentArray(JsonReader reader) throws Exception {
            reader.beginArray();
            while (reader.hasNext()) {
                Segment segment = new Segment();
                segment.deserialize(reader);
                ownerWorkerRef.tell(segment);
            }
            reader.endArray();
        }
    }
}
