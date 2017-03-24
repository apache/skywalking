package com.a.eye.skywalking.collector.worker.nodeinst.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.TimeSlice;
import com.a.eye.skywalking.collector.worker.nodeinst.NodeInstIndex;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

/**
 * @author pengys5
 */
public class NodeInstSummarySearchWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeInstSummarySearchWithTimeSlice.class);

    public NodeInstSummarySearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;

            SearchRequestBuilder searchRequestBuilder = EsClient.getClient().prepareSearch(NodeInstIndex.Index);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeInstIndex.Time_Slice).gte(search.getStartTime()).lte(search.getEndTime()));
            searchRequestBuilder.addAggregation(AggregationBuilders.terms("codes").field("code"));
            searchRequestBuilder.setSize(0);

            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            Terms agg = searchResponse.getAggregations().get("codes");

            JsonArray nodeInstCountArray = new JsonArray();
            for (Terms.Bucket entry : agg.getBuckets()) {
                JsonObject nodeInstCountObj = new JsonObject();
                String code = String.valueOf(entry.getKey());
                long count = entry.getDocCount();
                nodeInstCountObj.addProperty("code", code);
                nodeInstCountObj.addProperty("count", count);
                nodeInstCountArray.add(nodeInstCountObj);
                logger.info("code %s, count %s", code, count);
            }

            JsonObject resJsonObj = (JsonObject) response;
            resJsonObj.add("result", nodeInstCountArray);
        } else {
            throw new IllegalArgumentException("message instance must be RequestEntity");
        }
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long startTime, long endTime) {
            super(sliceType, startTime, endTime);
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeInstSummarySearchWithTimeSlice> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeInstSummarySearchWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeInstSummarySearchWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeInstSummarySearchWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
