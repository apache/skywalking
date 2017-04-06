package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.Const;
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
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

/**
 * @author pengys5
 */
public class NodeRefSearchWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeRefSearchWithTimeSlice.class);

    NodeRefSearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;

            SearchRequestBuilder searchRequestBuilder = EsClient.INSTANCE.getClient().prepareSearch(NodeRefIndex.Index);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeRefIndex.Time_Slice).gte(search.getStartTime()).lte(search.getEndTime()));
            searchRequestBuilder.setSize(0);

            searchRequestBuilder.addAggregation(AggregationBuilders.terms(NodeRefIndex.AGG_COLUMN).field(NodeRefIndex.AGG_COLUMN).size(100));

            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            Terms genders = searchResponse.getAggregations().get(NodeRefIndex.AGG_COLUMN);

            JsonArray nodeRefArray = new JsonArray();
            for (Terms.Bucket entry : genders.getBuckets()) {
                String aggId = entry.getKeyAsString();
                String[] aggIds = aggId.split(Const.IDS_SPLIT);
                String front = aggIds[0];
                String behind = aggIds[1];

                JsonObject nodeRefObj = new JsonObject();
                nodeRefObj.addProperty(NodeRefIndex.Front, front);
                nodeRefObj.addProperty(NodeRefIndex.Behind, behind);
                nodeRefArray.add(nodeRefObj);
            }
            logger.debug("node ref data: %s", nodeRefArray.toString());

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
