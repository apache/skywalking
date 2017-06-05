package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorker;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalAsyncWorkerRef;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.a.eye.skywalking.collector.worker.segment.entity.SegmentJsonReader;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */

public abstract class AbstractPost extends AbstractLocalAsyncWorker {

    public AbstractPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override final public void onWork(Object message) throws Exception {
        onReceive(message);
    }

    protected abstract void onReceive(Object message) throws Exception;

    static class PostWithHttpServlet extends AbstractHttpServlet {

        private Logger logger = LogManager.getFormatterLogger(PostWithHttpServlet.class);

        private final LocalAsyncWorkerRef ownerWorkerRef;

        PostWithHttpServlet(LocalAsyncWorkerRef ownerWorkerRef) {
            this.ownerWorkerRef = ownerWorkerRef;
        }

        @Override        final protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
            JsonObject resJson = new JsonObject();
            try {
                BufferedReader bufferedReader = request.getReader();
                streamReader(bufferedReader);
                reply(response, resJson, HttpServletResponse.SC_OK);
            } catch (Exception e) {
                logger.error(e);
                resJson.addProperty("error", e.getMessage());
                reply(response, resJson, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        private void streamReader(BufferedReader bufferedReader) throws Exception {
            try (SegmentJsonReader reader = new SegmentJsonReader(bufferedReader)) {
                readSegmentArray(reader);
            }
        }

        private void readSegmentArray(SegmentJsonReader reader) throws Exception {
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
