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
import org.skywalking.apm.collector.worker.Const;
import org.skywalking.apm.collector.worker.instance.InstanceIndex;
import org.skywalking.apm.collector.worker.instance.PingTimeIndex;
import org.skywalking.apm.collector.worker.storage.EsClient;

public class CountInstancesWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(CountInstancesWithTimeSlice.class);

    public CountInstancesWithTimeSlice(Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onWork(Object request, Object response) throws WorkerException {
        if (request instanceof RequestEntity) {
            RequestEntity requestEntity = (RequestEntity)request;
            SearchRequestBuilder searchRequestBuilder = EsClient.INSTANCE.getClient().prepareSearch(InstanceIndex.INDEX);
            searchRequestBuilder.setTypes(PingTimeIndex.TYPE_PING_TIME).setTypes(InstanceIndex.TYPE_REGISTRY);
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setSize(0);

            BoolQueryBuilder instanceCountQuery = QueryBuilders.boolQuery();

            BoolQueryBuilder registryTimeAndPingTimeWithinQueryTime = QueryBuilders.boolQuery();
            registryTimeAndPingTimeWithinQueryTime.must().add(QueryBuilders.rangeQuery(InstanceIndex.REGISTRY_TIME).gte(requestEntity.getStartTime()));
            registryTimeAndPingTimeWithinQueryTime.must().add(QueryBuilders.rangeQuery(PingTimeIndex.PING_TIME).lte(requestEntity.getEndTime()));

            BoolQueryBuilder registryTimeAndPingTimeOutsideQueryTime = QueryBuilders.boolQuery();
            registryTimeAndPingTimeOutsideQueryTime.must().add(QueryBuilders.rangeQuery(InstanceIndex.REGISTRY_TIME).lte(requestEntity.getEndTime()));
            registryTimeAndPingTimeOutsideQueryTime.must().add(QueryBuilders.rangeQuery(PingTimeIndex.PING_TIME).gte(requestEntity.getStartTime()));

            instanceCountQuery.should().add(QueryBuilders.rangeQuery(InstanceIndex.REGISTRY_TIME).gte(requestEntity.getStartTime()).lte(requestEntity.getEndTime()));
            instanceCountQuery.should().add(QueryBuilders.rangeQuery(PingTimeIndex.PING_TIME).gte(requestEntity.getStartTime()).lte(requestEntity.getEndTime()));
            instanceCountQuery.should().add(registryTimeAndPingTimeWithinQueryTime);
            instanceCountQuery.should().add(registryTimeAndPingTimeOutsideQueryTime);

            searchRequestBuilder.setQuery(instanceCountQuery);
            searchRequestBuilder.addAggregation(AggregationBuilders.terms(InstanceIndex.APPLICATION_CODE).field(InstanceIndex.APPLICATION_CODE));
            SearchResponse sr = searchRequestBuilder.execute().actionGet();
            Terms aggregator = sr.getAggregations().get(InstanceIndex.APPLICATION_CODE);

            JsonArray instancesArray = new JsonArray();
            for (Terms.Bucket bucket : aggregator.getBuckets()) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty(InstanceIndex.APPLICATION_CODE, bucket.getKeyAsString());
                jsonObject.addProperty("count", bucket.getDocCount());
                instancesArray.add(jsonObject);
            }

            JsonObject resJsonObj = (JsonObject)response;
            resJsonObj.add(Const.RESULT, instancesArray);
        } else {
            logger.error("unhandled message, message instance must InstanceCountSearchGroupWithTimeSlice.RequestEntity, but is %s", request.getClass().toString());
        }
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

    public static class Factory extends AbstractLocalSyncWorkerProvider<CountInstancesWithTimeSlice> {
        @Override
        public Role role() {
            return CountInstancesWithTimeSlice.WorkerRole.INSTANCE;
        }

        @Override
        public CountInstancesWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new CountInstancesWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return CountInstancesWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
