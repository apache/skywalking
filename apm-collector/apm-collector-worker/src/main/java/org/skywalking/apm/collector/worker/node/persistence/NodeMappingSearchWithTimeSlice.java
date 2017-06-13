package org.skywalking.apm.collector.worker.node.persistence;

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
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerException;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.Const;
import org.skywalking.apm.collector.worker.TimeSlice;
import org.skywalking.apm.collector.worker.node.NodeMappingIndex;
import org.skywalking.apm.collector.worker.storage.EsClient;

/**
 * @author pengys5
 */
public class NodeMappingSearchWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeMappingSearchWithTimeSlice.class);

    NodeMappingSearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws WorkerException {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity)request;

            SearchRequestBuilder searchRequestBuilder = EsClient.INSTANCE.getClient().prepareSearch(NodeMappingIndex.INDEX);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(NodeMappingIndex.TIME_SLICE).gte(search.getStartTime()).lte(search.getEndTime()));
            searchRequestBuilder.setSize(0);

            searchRequestBuilder.addAggregation(AggregationBuilders.terms(NodeMappingIndex.AGG_COLUMN).field(NodeMappingIndex.AGG_COLUMN).size(100));
            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            Terms genders = searchResponse.getAggregations().get(NodeMappingIndex.AGG_COLUMN);

            JsonArray nodeMappingArray = new JsonArray();
            for (Terms.Bucket entry : genders.getBuckets()) {
                String aggId = entry.getKeyAsString();
                String[] aggIds = aggId.split(Const.IDS_SPLIT);
                String code = aggIds[0];
                String peers = aggIds[1];

                JsonObject nodeMappingObj = new JsonObject();
                nodeMappingObj.addProperty(NodeMappingIndex.CODE, code);
                nodeMappingObj.addProperty(NodeMappingIndex.PEERS, peers);
                nodeMappingArray.add(nodeMappingObj);
            }
            logger.debug("node mapping data: %s", nodeMappingArray.toString());

            JsonObject resJsonObj = (JsonObject)response;
            resJsonObj.add(Const.RESULT, nodeMappingArray);
        } else {
            logger.error("unhandled message, message instance must NodeMappingSearchWithTimeSlice.RequestEntity, but is %s", request.getClass().toString());
        }
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long startTime, long endTime) {
            super(sliceType, startTime, endTime);
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeMappingSearchWithTimeSlice> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeMappingSearchWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeMappingSearchWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeMappingSearchWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
