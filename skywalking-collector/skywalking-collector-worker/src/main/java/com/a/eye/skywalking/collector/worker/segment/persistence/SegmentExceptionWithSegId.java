package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.segment.SegmentExceptionIndex;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonObject;
import org.elasticsearch.action.get.GetResponse;

/**
 * @author pengys5
 */
public class SegmentExceptionWithSegId extends AbstractLocalSyncWorker {

    private SegmentExceptionWithSegId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity)request;

            GetResponse getResponse = EsClient.INSTANCE.getClient().prepareGet(SegmentExceptionIndex.INDEX, SegmentExceptionIndex.TYPE_RECORD, search.segId).get();

            JsonObject dataJson = new JsonObject();
            dataJson.addProperty(SegmentExceptionIndex.SEG_ID, (String)getResponse.getSource().get(SegmentExceptionIndex.SEG_ID));
            dataJson.addProperty(SegmentExceptionIndex.IS_ERROR, (Boolean)getResponse.getSource().get(SegmentExceptionIndex.IS_ERROR));

            JsonObject resJsonObj = (JsonObject)response;
            resJsonObj.add("result", dataJson);
        }
    }

    public static class RequestEntity {
        private String segId;

        public RequestEntity(String segId) {
            this.segId = segId;
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<SegmentExceptionWithSegId> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SegmentExceptionWithSegId workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentExceptionWithSegId(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentExceptionWithSegId.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
