package org.skywalking.apm.collector.worker.span.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.get.GetResponse;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.Const;
import org.skywalking.apm.collector.worker.segment.SegmentIndex;
import org.skywalking.apm.collector.worker.segment.entity.Segment;
import org.skywalking.apm.collector.worker.segment.entity.SegmentDeserialize;
import org.skywalking.apm.collector.worker.segment.entity.Span;
import org.skywalking.apm.collector.worker.storage.GetResponseFromEs;

/**
 * @author pengys5
 */
public class SpanSearchWithId extends AbstractLocalSyncWorker {

    private Gson gson = new Gson();

    SpanSearchWithId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onWork(Object request, Object response) throws WorkerException {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity)request;
            GetResponse getResponse = GetResponseFromEs.INSTANCE.get(SegmentIndex.INDEX, SegmentIndex.TYPE_RECORD, search.segId);
            Segment segment;
            try {
                segment = SegmentDeserialize.INSTANCE.deserializeSingle(getResponse.getSourceAsString());
            } catch (IOException e) {
                throw new WorkerException(e.getMessage(), e);
            }
            List<Span> spanList = segment.getSpans();

            getResponse.getSource();
            JsonObject dataJson = new JsonObject();

            for (Span span : spanList) {
                if (String.valueOf(span.getSpanId()).equals(search.spanId)) {
                    String spanJsonStr = gson.toJson(span);
                    dataJson = gson.fromJson(spanJsonStr, JsonObject.class);
                }
            }

            JsonObject resJsonObj = (JsonObject)response;
            resJsonObj.add(Const.RESULT, dataJson);
        }
    }

    public static class RequestEntity {
        private String segId;
        private String spanId;

        public RequestEntity(String segId, String spanId) {
            this.segId = segId;
            this.spanId = spanId;
        }

        public String getSegId() {
            return segId;
        }

        public String getSpanId() {
            return spanId;
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<SpanSearchWithId> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SpanSearchWithId workerInstance(ClusterWorkerContext clusterContext) {
            return new SpanSearchWithId(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SpanSearchWithId.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
