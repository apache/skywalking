package org.skywalking.apm.collector.worker.instance.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.instance.InstanceIndex;
import org.skywalking.apm.collector.worker.storage.EsClient;

public class InstanceCountSearchGroupWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(InstanceCountSearchGroupWithTimeSlice.class);

    public InstanceCountSearchGroupWithTimeSlice(Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onWork(Object request, Object response) throws WorkerException {
        if (request instanceof RequestEntity) {
            RequestEntity requestEntity = (RequestEntity)request;
            SearchRequestBuilder searchRequestBuilder = EsClient.INSTANCE.getClient().prepareSearch(InstanceIndex.INDEX);
            searchRequestBuilder = searchRequestBuilder.setTypes(InstanceIndex.TYPE_RECORD);
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setSize(0);

            BoolQueryBuilder instanceQuery = QueryBuilders.boolQuery();
            instanceQuery.should().add(buildAliveInstanceQuery(requestEntity));
            instanceQuery.should().add(addDeadInstanceQuery(requestEntity));

            searchRequestBuilder.setQuery(instanceQuery);
            searchRequestBuilder.addAggregation(AggregationBuilders.terms(InstanceIndex.APPLICATION_CODE).field(InstanceIndex.APPLICATION_CODE));
            SearchResponse sr = searchRequestBuilder.execute().actionGet();
            Terms aggregator = sr.getAggregations().get(InstanceIndex.APPLICATION_CODE);

            JsonArray instancesArray = new JsonArray();
            for (Terms.Bucket bucket : aggregator.getBuckets()) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty(InstanceIndex.APPLICATION_CODE, bucket.getKeyAsString());
                jsonObject.addProperty(InstanceIndex.INSTANCE_COUNT, bucket.getDocCount());
                instancesArray.add(jsonObject);
            }

            JsonObject resJsonObj = (JsonObject)response;
            resJsonObj.add("result", instancesArray);
        } else {
            logger.error("unhandled message, message instance must NodeRefResSumGroupWithTimeSlice.RequestEntity, but is %s", request.getClass().toString());
        }
    }

    private BoolQueryBuilder addDeadInstanceQuery(RequestEntity requestEntity) {
        BoolQueryBuilder newBoolQuery = QueryBuilders.boolQuery();
        newBoolQuery.must().add(QueryBuilders.rangeQuery(InstanceIndex.REGISTRY_TIME).lte(requestEntity.getEndTime()));
        newBoolQuery.must().add(QueryBuilders.rangeQuery(InstanceIndex.PING_TIME).gte(requestEntity.getStartTime()));
        return newBoolQuery;
    }

    private BoolQueryBuilder buildAliveInstanceQuery(
        RequestEntity requestEntity) {
        BoolQueryBuilder booleanClauses = QueryBuilders.boolQuery();
        booleanClauses.must().add(QueryBuilders.rangeQuery(InstanceIndex.PING_TIME).gt(requestEntity.getStartTime() - 100));
        booleanClauses.must().add(QueryBuilders.rangeQuery(InstanceIndex.REGISTRY_TIME).lte(requestEntity.getEndTime()));
        return booleanClauses;
    }

    public static class RequestEntity {
        private long startTime;
        private long endTime;

        public RequestEntity(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<InstanceCountSearchGroupWithTimeSlice> {
        @Override
        public Role role() {
            return InstanceCountSearchGroupWithTimeSlice.WorkerRole.INSTANCE;
        }

        @Override
        public InstanceCountSearchGroupWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new InstanceCountSearchGroupWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return InstanceCountSearchGroupWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
