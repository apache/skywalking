package com.a.eye.skywalking.collector.worker.span.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.a.eye.skywalking.collector.worker.segment.entity.SegmentDeserialize;
import com.a.eye.skywalking.collector.worker.segment.entity.Span;
import com.a.eye.skywalking.collector.worker.storage.GetResponseFromEs;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.elasticsearch.action.get.GetResponse;

import java.util.List;

/**
 * @author pengys5
 */
public class SpanSearchWithId extends AbstractLocalSyncWorker {

    private Gson gson = new Gson();

    SpanSearchWithId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity)request;
            GetResponse getResponse = GetResponseFromEs.INSTANCE.get(SegmentIndex.INDEX, SegmentIndex.TYPE_RECORD, search.segId);
            Segment segment = SegmentDeserialize.INSTANCE.deserializeSingle(getResponse.getSourceAsString());
            List<Span> spanList = segment.getSpans();

            getResponse.getSource();
            JsonObject dataJson = new JsonObject();

            for (Span span : spanList) {
                if (String.valueOf(span.getSpanId()).equals(search.spanId)) {
                    span.setJsonStr("");
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
