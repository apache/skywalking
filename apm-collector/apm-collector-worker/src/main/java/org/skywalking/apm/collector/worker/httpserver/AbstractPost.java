package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import java.io.BufferedReader;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalSyncWorkerRef;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerRef;
import org.skywalking.apm.collector.worker.instance.entity.RegistryInfo;
import org.skywalking.apm.collector.worker.instance.entity.HeartBeat;
import org.skywalking.apm.collector.worker.segment.entity.Segment;

/**
 * @author pengys5
 */

public abstract class AbstractPost extends AbstractLocalSyncWorker {

    public AbstractPost(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onWork(Object request, Object response) throws Exception {
        try {
            onReceive(request, (JsonObject)response);
        } catch (Exception e) {
            ((JsonObject)response).addProperty("isSuccess", false);
            ((JsonObject)response).addProperty("reason", e.getMessage());
        }
    }

    protected abstract void onReceive(Object message, JsonObject response) throws Exception;

    public static class SegmentPostWithHttpServlet extends AbstractPostWithHttpServlet {

        public SegmentPostWithHttpServlet(WorkerRef ownerWorkerRef) {
            super(ownerWorkerRef);
        }

        protected void doWork(BufferedReader bufferedReader, JsonObject resJson) throws Exception {
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

    public static class RegisterPostWithHttpServlet extends AbstractPostWithHttpServlet {

        public RegisterPostWithHttpServlet(WorkerRef ownerWorkerRef) {
            super(ownerWorkerRef);
        }

        @Override
        protected void doWork(BufferedReader bufferedReader, JsonObject resJson) throws Exception {
            JsonReader reader = new JsonReader(bufferedReader);
            reader.beginObject();
            if (reader.nextName().equals("ac")) {
                RegistryInfo registryParam = new RegistryInfo(reader.nextString());
                ((LocalSyncWorkerRef)ownerWorkerRef).ask(registryParam, resJson);
            }
            reader.endObject();
        }

    }

    public static class HeartBeatPostWithHttpServlet extends AbstractPostWithHttpServlet {

        public HeartBeatPostWithHttpServlet(WorkerRef ownerWorkerRef) {
            super(ownerWorkerRef);
        }

        @Override
       protected void doWork(BufferedReader bufferedReader, JsonObject resJson) throws Exception {
            JsonReader reader = new JsonReader(bufferedReader);
            reader.beginObject();
            if (reader.nextName().equals("ac")) {
                HeartBeat registryParam = new HeartBeat(reader.nextString());
                ownerWorkerRef.tell(registryParam);
            }
        }

    }

}
