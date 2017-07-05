package org.skywalking.apm.collector.worker.segment.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.segment.SegmentCostIndex;
import org.skywalking.apm.collector.worker.segment.SegmentExceptionIndex;
import org.skywalking.apm.collector.worker.segment.SegmentIndex;
import org.skywalking.apm.collector.worker.segment.entity.SegmentDeserialize;
import org.skywalking.apm.collector.worker.storage.EsClient;
import org.skywalking.apm.collector.worker.tools.CollectionTools;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.util.StringUtil;

/**
 * @author pengys5
 */
public class SegmentTopSearch extends AbstractLocalSyncWorker {

    private SegmentTopSearch(Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(SegmentExceptionWithSegId.WorkerRole.INSTANCE).create(this);
    }

    @Override
    protected void onWork(Object request, Object response) throws WorkerException {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity)request;

            SearchRequestBuilder searchRequestBuilder = EsClient.INSTANCE.getClient().prepareSearch(SegmentCostIndex.INDEX);
            searchRequestBuilder.setTypes(SegmentCostIndex.TYPE_RECORD);
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            searchRequestBuilder.setQuery(boolQueryBuilder);
            List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

            mustQueryList.add(QueryBuilders.rangeQuery(SegmentCostIndex.TIME_SLICE).gte(search.startTime).lte(search.endTime));
            if (search.minCost != -1 || search.maxCost != -1) {
                RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(SegmentCostIndex.COST);
                if (search.minCost != -1) {
                    rangeQueryBuilder.gte(search.minCost);
                }
                if (search.maxCost != -1) {
                    rangeQueryBuilder.lte(search.maxCost);
                }
                boolQueryBuilder.must().add(rangeQueryBuilder);
            }
            if (!StringUtil.isEmpty(search.operationName)) {
                mustQueryList.add(QueryBuilders.matchQuery(SegmentCostIndex.OPERATION_NAME, search.operationName));
            }
            if (!StringUtil.isEmpty(search.globalTraceId)) {
                mustQueryList.add(QueryBuilders.matchQuery(SegmentCostIndex.GLOBAL_TRACE_ID, search.globalTraceId));
            }

            searchRequestBuilder.addSort(SegmentCostIndex.COST, SortOrder.DESC);
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
                String segId = (String)searchHit.getSource().get(SegmentCostIndex.SEG_ID);
                topSegmentJson.addProperty(SegmentCostIndex.SEG_ID, segId);
                topSegmentJson.addProperty(SegmentCostIndex.START_TIME, (Number)searchHit.getSource().get(SegmentCostIndex.START_TIME));
                if (searchHit.getSource().containsKey(SegmentCostIndex.END_TIME)) {
                    topSegmentJson.addProperty(SegmentCostIndex.END_TIME, (Number)searchHit.getSource().get(SegmentCostIndex.END_TIME));
                }

                topSegmentJson.addProperty(SegmentCostIndex.OPERATION_NAME, (String)searchHit.getSource().get(SegmentCostIndex.OPERATION_NAME));
                topSegmentJson.addProperty(SegmentCostIndex.COST, (Number)searchHit.getSource().get(SegmentCostIndex.COST));

                GetResponse getResponse = EsClient.INSTANCE.getClient().prepareGet(SegmentIndex.INDEX, SegmentIndex.TYPE_RECORD, segId).get();
                String segmentObjBlob = (String)getResponse.getSource().get(SegmentIndex.SEGMENT_OBJ_BLOB);

                TraceSegmentObject segment = SegmentDeserialize.INSTANCE.deserializeSingle(segmentObjBlob);
                List<String> distributedTraceIdList = segment.getGlobalTraceIdsList();

                JsonArray distributedTraceIdArray = new JsonArray();
                if (CollectionTools.isNotEmpty(distributedTraceIdList)) {
                    for (String distributedTraceId : distributedTraceIdList) {
                        distributedTraceIdArray.add(distributedTraceId);
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

            JsonObject resJsonObj = (JsonObject)response;
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
        private String globalTraceId;
        private String operationName;

        public RequestEntity(int from, int limit, long startTime, long endTime, String globalTraceId,
            String operationName) {
            this.from = from;
            this.limit = limit;
            this.startTime = startTime;
            this.endTime = endTime;
            this.globalTraceId = globalTraceId;
            this.operationName = operationName;
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

    public static class Factory extends AbstractLocalSyncWorkerProvider<SegmentTopSearch> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public SegmentTopSearch workerInstance(ClusterWorkerContext clusterContext) {
            return new SegmentTopSearch(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return SegmentTopSearch.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
