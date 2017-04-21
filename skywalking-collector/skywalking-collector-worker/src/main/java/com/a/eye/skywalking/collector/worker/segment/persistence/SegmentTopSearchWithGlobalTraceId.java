package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentCostIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentExceptionIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.segment.entity.GlobalTraceId;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.a.eye.skywalking.collector.worker.segment.entity.SegmentDeserialize;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.MergeData;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;

import java.util.List;

/**
 * @author pengys5
 */
public class SegmentTopSearchWithGlobalTraceId extends AbstractLocalSyncWorker {

    private Gson gson = new Gson();

    private SegmentTopSearchWithGlobalTraceId(Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentExceptionWithSegId.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity)request;
            Client client = EsClient.INSTANCE.getClient();

            String globalTraceData = client.prepareGet(GlobalTraceIndex.INDEX, GlobalTraceIndex.TYPE_RECORD, search.globalTraceId).get().getSourceAsString();
            JsonObject globalTraceObj = gson.fromJson(globalTraceData, JsonObject.class);

            JsonObject topSegPaging = new JsonObject();
            topSegPaging.addProperty("recordsTotal", 0);

            JsonArray topSegArray = new JsonArray();
            topSegPaging.add("data", topSegArray);

            if (globalTraceObj != null && globalTraceObj.has(GlobalTraceIndex.SUB_SEG_IDS)) {
                String subSegIdsStr = globalTraceObj.get(GlobalTraceIndex.SUB_SEG_IDS).getAsString();
                String[] subSegIds = subSegIdsStr.split(MergeData.SPLIT);

                topSegPaging.addProperty("recordsTotal", subSegIds.length);

                int num = search.from;
                int limit = search.limit;
                if (search.limit >= subSegIds.length) {
                    limit = subSegIds.length;
                }

                for (int i = num; i < limit; i++) {
                    GetResponse getResponse = client.prepareGet(SegmentCostIndex.INDEX, SegmentCostIndex.TYPE_RECORD, subSegIds[num]).get();

                    JsonObject topSegmentJson = new JsonObject();
                    topSegmentJson.addProperty("num", num);
                    String segId = (String)getResponse.getSource().get(SegmentCostIndex.SEG_ID);
                    topSegmentJson.addProperty(SegmentCostIndex.SEG_ID, segId);
                    topSegmentJson.addProperty(SegmentCostIndex.START_TIME, (Number)getResponse.getSource().get(SegmentCostIndex.START_TIME));
                    if (getResponse.getSource().containsKey(SegmentCostIndex.END_TIME)) {
                        topSegmentJson.addProperty(SegmentCostIndex.END_TIME, (Number)getResponse.getSource().get(SegmentCostIndex.END_TIME));
                    }

                    topSegmentJson.addProperty(SegmentCostIndex.OPERATION_NAME, (String)getResponse.getSource().get(SegmentCostIndex.OPERATION_NAME));
                    topSegmentJson.addProperty(SegmentCostIndex.COST, (Number)getResponse.getSource().get(SegmentCostIndex.COST));

                    String segmentSource = client.prepareGet(SegmentIndex.INDEX, SegmentIndex.TYPE_RECORD, segId).get().getSourceAsString();
                    Segment segment = SegmentDeserialize.INSTANCE.deserializeSingle(segmentSource);
                    List<GlobalTraceId> distributedTraceIdList = segment.getRelatedGlobalTraces();

                    JsonArray distributedTraceIdArray = new JsonArray();
                    if (CollectionTools.isNotEmpty(distributedTraceIdList)) {
                        for (GlobalTraceId distributedTraceId : distributedTraceIdList) {
                            distributedTraceIdArray.add(distributedTraceId.get());
                        }
                    }
                    topSegmentJson.add("traceIds", distributedTraceIdArray);

                    boolean isError = false;
                    JsonObject resJsonObj = new JsonObject();
                    getSelfContext().lookup(SegmentExceptionWithSegId.WorkerRole.INSTANCE).ask(new SegmentExceptionWithSegId.RequestEntity(segId), resJsonObj);
                    if (resJsonObj.has("result")) {
                        JsonObject segExJson = resJsonObj.get("result").getAsJsonObject();
                        if (segExJson.has(SegmentExceptionIndex.IS_ERROR)) {
                            isError = segExJson.get(SegmentExceptionIndex.IS_ERROR).getAsBoolean();
                        }
                    }
                    topSegmentJson.addProperty(SegmentExceptionIndex.IS_ERROR, isError);

                    num++;
                    topSegArray.add(topSegmentJson);
                }
            }

            JsonObject resJsonObj = (JsonObject)response;
            resJsonObj.add("result", topSegPaging);
        }
    }

    public static class RequestEntity {
        private int from;
        private int limit;
        private String globalTraceId;

        public RequestEntity(String globalTraceId, int from, int limit) {
            this.from = from;
            this.limit = limit;
            this.globalTraceId = globalTraceId;
        }

        public int getFrom() {
            return from;
        }

        public int getLimit() {
            return limit;
        }

        public String getGlobalTraceId() {
            return globalTraceId;
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<SegmentTopSearchWithGlobalTraceId> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SegmentTopSearchWithGlobalTraceId workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentTopSearchWithGlobalTraceId(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentTopSearchWithGlobalTraceId.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
