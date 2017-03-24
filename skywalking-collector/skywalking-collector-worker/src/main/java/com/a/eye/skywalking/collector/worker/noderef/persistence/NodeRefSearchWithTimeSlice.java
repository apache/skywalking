package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.TimeSlice;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;
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
public class NodeRefSearchWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeRefSearchWithTimeSlice.class);

    private NodeRefSearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;

            SearchRequestBuilder searchRequestBuilder = EsClient.getClient().prepareSearch(NodeRefIndex.Index);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeRefIndex.Time_Slice).gte(search.getStartTime()).lte(search.getEndTime()));
            searchRequestBuilder.setSize(0);

            AggregationBuilder aggregation = AggregationBuilders.terms(NodeRefIndex.AGG_COLUMN).field(NodeRefIndex.AGG_COLUMN);
            aggregation.subAggregation(AggregationBuilders.topHits(NodeRefIndex.Top_One).size(1));
            searchRequestBuilder.addAggregation(aggregation);

            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            JsonArray nodeRefArray = new JsonArray();
            Terms agg = searchResponse.getAggregations().get(NodeRefIndex.AGG_COLUMN);
            for (Terms.Bucket entry : agg.getBuckets()) {
                TopHits topHits = entry.getAggregations().get(NodeRefIndex.Top_One);
                for (SearchHit hit : topHits.getHits().getHits()) {
                    logger.debug(" -> id [{%s}], _source [{%s}]", hit.getId(), hit.getSourceAsString());

                    JsonObject nodeRefObj = new JsonObject();
                    nodeRefObj.addProperty(NodeRefIndex.Front, (String) hit.getSource().get(NodeRefIndex.Front));
                    nodeRefObj.addProperty(NodeRefIndex.FrontIsRealCode, (Boolean) hit.getSource().get(NodeRefIndex.FrontIsRealCode));
                    nodeRefObj.addProperty(NodeRefIndex.Behind, (String) hit.getSource().get(NodeRefIndex.Behind));
                    nodeRefObj.addProperty(NodeRefIndex.BehindIsRealCode, (Boolean) hit.getSource().get(NodeRefIndex.BehindIsRealCode));
                    nodeRefObj.addProperty(NodeRefIndex.Time_Slice, (Long) hit.getSource().get(NodeRefIndex.Time_Slice));
                    nodeRefArray.add(nodeRefObj);
                }
            }

            JsonObject resJsonObj = (JsonObject) response;
            resJsonObj.add("result", nodeRefArray);
        } else {
            throw new IllegalArgumentException("message instance must be RequestEntity");
        }
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long startTime, long endTime) {
            super(sliceType, startTime, endTime);
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeRefSearchWithTimeSlice> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeRefSearchWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefSearchWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefSearchWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
