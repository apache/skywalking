package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentCostIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentExceptionIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.segment.logic.Segment;
import com.a.eye.skywalking.collector.worker.segment.logic.SegmentDeserialize;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.MergeData;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.a.eye.skywalking.trace.TraceId.DistributedTraceId;
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

    private SegmentTopSearchWithGlobalTraceId(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentExceptionWithSegId.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;
            Client client = EsClient.INSTANCE.getClient();

            String globalTraceData = client.prepareGet(GlobalTraceIndex.Index, GlobalTraceIndex.Type_Record, search.globalTraceId).get().getSourceAsString();
            JsonObject globalTraceObj = gson.fromJson(globalTraceData, JsonObject.class);

            JsonObject topSegPaging = new JsonObject();
            topSegPaging.addProperty("recordsTotal", 0);

            JsonArray topSegArray = new JsonArray();
            topSegPaging.add("data", topSegArray);

            if (globalTraceObj != null && globalTraceObj.has(GlobalTraceIndex.SubSegIds)) {
                String subSegIdsStr = globalTraceObj.get(GlobalTraceIndex.SubSegIds).getAsString();
                String[] subSegIds = subSegIdsStr.split(MergeData.Split);

                topSegPaging.addProperty("recordsTotal", subSegIds.length);

                int num = search.from;
                int limit = search.limit;
                if (search.limit >= subSegIds.length) {
                    limit = subSegIds.length;
                }

                for (int i = num; i < limit; i++) {
                    GetResponse getResponse = client.prepareGet(SegmentCostIndex.Index, SegmentCostIndex.Type_Record, subSegIds[num]).get();

                    JsonObject topSegmentJson = new JsonObject();
                    topSegmentJson.addProperty("num", num);
                    String segId = (String) getResponse.getSource().get(SegmentCostIndex.SegId);
                    topSegmentJson.addProperty(SegmentCostIndex.SegId, segId);
                    topSegmentJson.addProperty(SegmentCostIndex.StartTime, (Number) getResponse.getSource().get(SegmentCostIndex.StartTime));
                    if (getResponse.getSource().containsKey(SegmentCostIndex.EndTime)) {
                        topSegmentJson.addProperty(SegmentCostIndex.EndTime, (Number) getResponse.getSource().get(SegmentCostIndex.EndTime));
                    }

                    topSegmentJson.addProperty(SegmentCostIndex.OperationName, (String) getResponse.getSource().get(SegmentCostIndex.OperationName));
                    topSegmentJson.addProperty(SegmentCostIndex.Cost, (Number) getResponse.getSource().get(SegmentCostIndex.Cost));

                    String segmentSource = client.prepareGet(SegmentIndex.Index, SegmentIndex.Type_Record, segId).get().getSourceAsString();
                    Segment segment = SegmentDeserialize.INSTANCE.deserializeFromES(segmentSource);
                    List<DistributedTraceId> distributedTraceIdList = segment.getRelatedGlobalTraces();

                    JsonArray distributedTraceIdArray = new JsonArray();
                    if (CollectionTools.isNotEmpty(distributedTraceIdList)) {
                        for (DistributedTraceId distributedTraceId : distributedTraceIdList) {
                            distributedTraceIdArray.add(distributedTraceId.get());
                        }
                    }
                    topSegmentJson.add("traceIds", distributedTraceIdArray);

                    boolean isError = false;
                    JsonObject resJsonObj = new JsonObject();
                    getSelfContext().lookup(SegmentExceptionWithSegId.WorkerRole.INSTANCE).ask(new SegmentExceptionWithSegId.RequestEntity(segId), resJsonObj);
                    if (resJsonObj.has("result")) {
                        JsonObject segExJson = resJsonObj.get("result").getAsJsonObject();
                        if (segExJson.has(SegmentExceptionIndex.IsError)) {
                            isError = segExJson.get(SegmentExceptionIndex.IsError).getAsBoolean();
                        }
                    }
                    topSegmentJson.addProperty(SegmentExceptionIndex.IsError, isError);

                    num++;
                    topSegArray.add(topSegmentJson);
                }
            }


            JsonObject resJsonObj = (JsonObject) response;
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
