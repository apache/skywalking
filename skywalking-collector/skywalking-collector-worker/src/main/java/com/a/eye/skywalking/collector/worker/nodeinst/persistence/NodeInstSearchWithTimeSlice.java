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
import org.elasticsearch.search.SearchHit;

/**
 * @author pengys5
 */
public class NodeInstSearchWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeInstSearchWithTimeSlice.class);

    public NodeInstSearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;
            SearchRequestBuilder searchRequestBuilder = EsClient.getClient().prepareSearch(NodeInstIndex.Index);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.termQuery("timeSlice", search.getTimeSlice()));
            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            SearchHit[] hits = searchResponse.getHits().getHits();
            logger.debug("dag node list size: %s", hits.length);

            JsonArray nodeInstArray = new JsonArray();
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                JsonObject nodeInstObj = new JsonObject();
                nodeInstObj.addProperty("code", (String) hit.getSource().get("code"));
                nodeInstObj.addProperty("clientCode", (String) hit.getSource().get("clientCode"));
                nodeInstObj.addProperty("kind", (String) hit.getSource().get("kind"));
                nodeInstObj.addProperty("component", (String) hit.getSource().get("component"));
                nodeInstObj.addProperty("address", (String) hit.getSource().get("address"));
                nodeInstObj.addProperty("timeSlice", (Long) hit.getSource().get("timeSlice"));
                nodeInstArray.add(nodeInstObj);
            }

            JsonObject resJsonObj = (JsonObject) response;
            resJsonObj.add("result", nodeInstArray);
        } else {
            throw new IllegalArgumentException("message instance must be RequestEntity");
        }
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long timeSlice) {
            super(sliceType, timeSlice);
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeInstSearchWithTimeSlice> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeInstSearchWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeInstSearchWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeInstSearchWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
