package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.segment.SegmentCostIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentExceptionIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.segment.logic.Segment;
import com.a.eye.skywalking.collector.worker.segment.logic.SegmentDeserialize;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.tools.CollectionTools;
import com.a.eye.skywalking.trace.TraceId.DistributedTraceId;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;

import java.util.List;

/**
 * @author pengys5
 */
public class SegmentTopSearchWithTimeSlice extends AbstractLocalSyncWorker {

    private SegmentTopSearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
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

            SearchRequestBuilder searchRequestBuilder = EsClient.INSTANCE.getClient().prepareSearch(SegmentCostIndex.Index);
            searchRequestBuilder.setTypes(SegmentCostIndex.Type_Record);
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            searchRequestBuilder.setQuery(boolQueryBuilder);
            boolQueryBuilder.must().add(QueryBuilders.rangeQuery(SegmentCostIndex.Time_Slice).gte(search.startTime).lte(search.endTime));
            if (search.minCost != -1 || search.maxCost != -1) {
                RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(SegmentCostIndex.Cost);
                if (search.minCost != -1) {
                    rangeQueryBuilder.gte(search.minCost);
                }
                if (search.maxCost != -1) {
                    rangeQueryBuilder.lte(search.maxCost);
                }
                boolQueryBuilder.must().add(rangeQueryBuilder);
            }

            searchRequestBuilder.addSort(SegmentCostIndex.Cost, SortOrder.DESC);
            searchRequestBuilder.setSize(search.limit);
            searchRequestBuilder.setFrom(search.from);

            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            JsonObject topSegPaging = new JsonObject();
            topSegPaging.addProperty("recordsTotal", searchResponse.getHits().totalHits());

            JsonArray topSegArray = new JsonArray();
            topSegPaging.add("data", topSegArray);

            int num = search.from;
            for (SearchHit searchHit : searchResponse.getHits().getHits()) {
                JsonObject topSegmentJson = new JsonObject();
                topSegmentJson.addProperty("num", num);
                String segId = (String) searchHit.getSource().get(SegmentCostIndex.SegId);
                topSegmentJson.addProperty(SegmentCostIndex.SegId, segId);
                topSegmentJson.addProperty(SegmentCostIndex.StartTime, (Number) searchHit.getSource().get(SegmentCostIndex.StartTime));
                if (searchHit.getSource().containsKey(SegmentCostIndex.EndTime)) {
                    topSegmentJson.addProperty(SegmentCostIndex.EndTime, (Number) searchHit.getSource().get(SegmentCostIndex.EndTime));
                }

                topSegmentJson.addProperty(SegmentCostIndex.OperationName, (String) searchHit.getSource().get(SegmentCostIndex.OperationName));
                topSegmentJson.addProperty(SegmentCostIndex.Cost, (Number) searchHit.getSource().get(SegmentCostIndex.Cost));

                String segmentSource = EsClient.INSTANCE.getClient().prepareGet(SegmentIndex.Index, SegmentIndex.Type_Record, segId).get().getSourceAsString();
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

            JsonObject resJsonObj = (JsonObject) response;
            resJsonObj.add("result", topSegPaging);
        }
    }

    public static class RequestEntity {
        private int from;
        private int limit;
        private long startTime;
        private long endTime;
        private int minCost;
        private int maxCost;

        public RequestEntity(int from, int limit, long startTime, long endTime) {
            this.from = from;
            this.limit = limit;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public void setMinCost(int minCost) {
            this.minCost = minCost;
        }

        public void setMaxCost(int maxCost) {
            this.maxCost = maxCost;
        }

        public int getFrom() {
            return from;
        }

        public int getLimit() {
            return limit;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public int getMinCost() {
            return minCost;
        }

        public int getMaxCost() {
            return maxCost;
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<SegmentTopSearchWithTimeSlice> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SegmentTopSearchWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentTopSearchWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentTopSearchWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
