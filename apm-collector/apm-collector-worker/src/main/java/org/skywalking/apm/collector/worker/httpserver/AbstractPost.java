package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalAsyncWorkerRef;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.worker.segment.entity.Segment;
import org.skywalking.apm.collector.worker.segment.entity.SegmentAndJson;
import org.skywalking.apm.collector.worker.segment.entity.SegmentDeserialize;

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

        @Override final protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
            JsonObject resJson = new JsonObject();
            try {
                BufferedReader bufferedReader = request.getReader();
                streamReader(bufferedReader);
                reply(response, resJson, HttpServletResponse.SC_OK);
            } catch (Exception e) {
                logger.error(e, e);
                resJson.addProperty("error", e.getMessage());
                reply(response, resJson, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        private void streamReader(BufferedReader bufferedReader) throws Exception {
            Segment segment;
            do {
                int character;
                StringBuilder builder = new StringBuilder();
                while ((character = bufferedReader.read()) != ' ') {
                    if (character == -1) {
                        return;
                    }
                    builder.append((char)character);
                }

                int length = Integer.valueOf(builder.toString());
                builder = new StringBuilder();

                char[] buffer = new char[length];
                int readLength = bufferedReader.read(buffer, 0, length);
                if (readLength != length) {
                    logger.error("The actual data length was different from the length in data head! ");
                    return;
                }
                builder.append(buffer);

                String segmentJsonStr = builder.toString();
                segment = SegmentDeserialize.INSTANCE.deserializeSingle(segmentJsonStr);

                ownerWorkerRef.tell(new SegmentAndJson(segment, segmentJsonStr));
            }
            while (segment != null);
        }
    }
}
