package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.TimeSlice;
import com.a.eye.skywalking.collector.worker.node.NodeIndex;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;

/**
 * @author pengys5
 */
public class NodeSearchWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeSearchWithTimeSlice.class);

    private NodeSearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;

            SearchRequestBuilder searchRequestBuilder = EsClient.getClient().prepareSearch(NodeIndex.Index);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeIndex.Time_Slice).gte(search.getStartTime()).lte(search.getEndTime()));
            searchRequestBuilder.setSize(0);

            AggregationBuilder aggregation = AggregationBuilders.terms(NodeIndex.AGG_COLUMN).field(NodeIndex.AGG_COLUMN);
            aggregation.subAggregation(AggregationBuilders.topHits(NodeIndex.Top_One).size(1));
            searchRequestBuilder.addAggregation(aggregation);

            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            JsonArray nodeArray = new JsonArray();
            Terms agg = searchResponse.getAggregations().get(NodeIndex.AGG_COLUMN);
            for (Terms.Bucket entry : agg.getBuckets()) {
                TopHits topHits = entry.getAggregations().get(NodeIndex.Top_One);
                for (SearchHit hit : topHits.getHits().getHits()) {
                    logger.debug(" -> id [{%s}], _source [{%s}]", hit.getId(), hit.getSourceAsString());

                    JsonObject nodeObj = new JsonObject();
                    nodeObj.addProperty(NodeIndex.Code, (String) hit.getSource().get(NodeIndex.Code));
                    if (hit.getSource().containsKey(NodeIndex.Component)) {
                        nodeObj.addProperty(NodeIndex.Component, (String) hit.getSource().get(NodeIndex.Component));
                    }
                    nodeObj.addProperty(NodeIndex.NickName, (String) hit.getSource().get(NodeIndex.NickName));
                    nodeObj.addProperty(NodeIndex.Time_Slice, (Long) hit.getSource().get(NodeIndex.Time_Slice));
                    nodeArray.add(nodeObj);
                }
            }

            JsonObject resJsonObj = (JsonObject) response;
            resJsonObj.add("result", nodeArray);
        } else {
            throw new IllegalArgumentException("message instance must be RequestEntity");
        }
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long startTime, long endTime) {
            super(sliceType, startTime, endTime);
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeSearchWithTimeSlice> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeSearchWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeSearchWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeSearchWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
