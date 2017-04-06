package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.TimeSlice;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefResSumIndex;
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
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;

/**
 * @author pengys5
 */
public class NodeRefResSumGroupWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeRefResSumGroupWithTimeSlice.class);

    NodeRefResSumGroupWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;

            SearchRequestBuilder searchRequestBuilder = EsClient.INSTANCE.getClient().prepareSearch(NodeRefResSumIndex.Index);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeRefResSumIndex.Time_Slice).gte(search.getStartTime()).lte(search.getEndTime()));
            searchRequestBuilder.setSize(0);

            TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(NodeRefResSumIndex.Time_Slice).field(NodeRefResSumIndex.Time_Slice);
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.OneSecondLess).field(NodeRefResSumIndex.OneSecondLess));
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.ThreeSecondLess).field(NodeRefResSumIndex.ThreeSecondLess));
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.FiveSecondLess).field(NodeRefResSumIndex.FiveSecondLess));
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.FiveSecondGreater).field(NodeRefResSumIndex.FiveSecondGreater));
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.Error).field(NodeRefResSumIndex.Error));
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.Summary).field(NodeRefResSumIndex.Summary));

            searchRequestBuilder.addAggregation(aggregationBuilder);

            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            JsonArray nodeRefResSumArray = new JsonArray();
            Terms aggTerms = searchResponse.getAggregations().get(NodeRefResSumIndex.Time_Slice);
            for (Terms.Bucket bucket : aggTerms.getBuckets()) {
                String aggId = String.valueOf(bucket.getKey());
                Sum oneSecondLess = bucket.getAggregations().get(NodeRefResSumIndex.OneSecondLess);
                Sum threeSecondLess = bucket.getAggregations().get(NodeRefResSumIndex.ThreeSecondLess);
                Sum fiveSecondLess = bucket.getAggregations().get(NodeRefResSumIndex.FiveSecondLess);
                Sum fiveSecondGreater = bucket.getAggregations().get(NodeRefResSumIndex.FiveSecondGreater);
                Sum error = bucket.getAggregations().get(NodeRefResSumIndex.Error);
                Sum summary = bucket.getAggregations().get(NodeRefResSumIndex.Summary);
                logger.debug("aggId: %s, oneSecondLess: %s, threeSecondLess: %s, fiveSecondLess: %s, fiveSecondGreater: %s, error: %s, summary: %s", aggId,
                        oneSecondLess.getValue(), threeSecondLess.getValue(), fiveSecondLess.getValue(), fiveSecondGreater.getValue(), error.getValue(), summary.getValue());

                JsonObject nodeRefResSumObj = new JsonObject();
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.Time_Slice, aggId);
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.OneSecondLess, oneSecondLess.getValue());
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.ThreeSecondLess, threeSecondLess.getValue());
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.FiveSecondLess, fiveSecondLess.getValue());
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.FiveSecondGreater, fiveSecondGreater.getValue());
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.Error, error.getValue());
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.Summary, summary.getValue());
                nodeRefResSumArray.add(nodeRefResSumObj);
            }

            JsonObject resJsonObj = (JsonObject) response;
            resJsonObj.add("result", nodeRefResSumArray);
        } else {
            throw new IllegalArgumentException("message instance must be RequestEntity");
        }
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long startTime, long endTime) {
            super(sliceType, startTime, endTime);
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeRefResSumGroupWithTimeSlice> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeRefResSumGroupWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefResSumGroupWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefResSumGroupWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
