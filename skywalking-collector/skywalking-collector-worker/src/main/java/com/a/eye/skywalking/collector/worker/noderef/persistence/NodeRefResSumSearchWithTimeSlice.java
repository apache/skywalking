package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorker;
import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorkerProvider;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.Const;
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
public class NodeRefResSumSearchWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeRefResSumSearchWithTimeSlice.class);

    NodeRefResSumSearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity)request;

            SearchRequestBuilder searchRequestBuilder = EsClient.INSTANCE.getClient().prepareSearch(NodeRefResSumIndex.INDEX);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeRefResSumIndex.TIME_SLICE).gte(search.getStartTime()).lte(search.getEndTime()));
            searchRequestBuilder.setSize(0);

            TermsAggregationBuilder aggregationBuilder = AggregationBuilders.terms(NodeRefResSumIndex.AGG_COLUMN).field(NodeRefResSumIndex.AGG_COLUMN);
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.ONE_SECOND_LESS).field(NodeRefResSumIndex.ONE_SECOND_LESS));
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.THREE_SECOND_LESS).field(NodeRefResSumIndex.THREE_SECOND_LESS));
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.FIVE_SECOND_LESS).field(NodeRefResSumIndex.FIVE_SECOND_LESS));
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.FIVE_SECOND_GREATER).field(NodeRefResSumIndex.FIVE_SECOND_GREATER));
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.ERROR).field(NodeRefResSumIndex.ERROR));
            aggregationBuilder.subAggregation(AggregationBuilders.sum(NodeRefResSumIndex.SUMMARY).field(NodeRefResSumIndex.SUMMARY));

            searchRequestBuilder.addAggregation(aggregationBuilder);

            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            JsonArray nodeRefResSumArray = new JsonArray();
            Terms aggTerms = searchResponse.getAggregations().get(NodeRefResSumIndex.AGG_COLUMN);
            for (Terms.Bucket bucket : aggTerms.getBuckets()) {
                String aggId = String.valueOf(bucket.getKey());
                Sum oneSecondLess = bucket.getAggregations().get(NodeRefResSumIndex.ONE_SECOND_LESS);
                Sum threeSecondLess = bucket.getAggregations().get(NodeRefResSumIndex.THREE_SECOND_LESS);
                Sum fiveSecondLess = bucket.getAggregations().get(NodeRefResSumIndex.FIVE_SECOND_LESS);
                Sum fiveSecondGreater = bucket.getAggregations().get(NodeRefResSumIndex.FIVE_SECOND_GREATER);
                Sum error = bucket.getAggregations().get(NodeRefResSumIndex.ERROR);
                Sum summary = bucket.getAggregations().get(NodeRefResSumIndex.SUMMARY);
                logger.debug("aggId: %s, oneSecondLess: %s, threeSecondLess: %s, fiveSecondLess: %s, fiveSecondGreater: %s, error: %s, summary: %s", aggId,
                    oneSecondLess.getValue(), threeSecondLess.getValue(), fiveSecondLess.getValue(), fiveSecondGreater.getValue(), error.getValue(), summary.getValue());

                JsonObject nodeRefResSumObj = new JsonObject();
                String[] ids = aggId.split(Const.IDS_SPLIT);
                String front = ids[0];
                String behind = ids[1];

                nodeRefResSumObj.addProperty("front", front);
                nodeRefResSumObj.addProperty("behind", behind);

                nodeRefResSumObj.addProperty(NodeRefResSumIndex.ONE_SECOND_LESS, oneSecondLess.getValue());
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.THREE_SECOND_LESS, threeSecondLess.getValue());
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.FIVE_SECOND_LESS, fiveSecondLess.getValue());
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.FIVE_SECOND_GREATER, fiveSecondGreater.getValue());
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.ERROR, error.getValue());
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.SUMMARY, summary.getValue());
                nodeRefResSumArray.add(nodeRefResSumObj);
            }

            JsonObject resJsonObj = (JsonObject)response;
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

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeRefResSumSearchWithTimeSlice> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeRefResSumSearchWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeRefResSumSearchWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeRefResSumSearchWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
