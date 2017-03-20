package com.a.eye.skywalking.collector.worker.dagnode.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.TimeSlice;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.index.ClientNodeIndex;
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
public class ClientNodeSearchPersistence extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(ClientNodeSearchPersistence.class);

    public ClientNodeSearchPersistence(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;
            JsonObject resJsonObj = (JsonObject) response;
            JsonArray result = search(search.getSliceType(), search.getTimeSlice());
            resJsonObj.add("result", result);
        } else {
            throw new IllegalArgumentException("message instance must be RequestEntity");
        }
    }

    public JsonArray search(String type, long timeSlice) {
        SearchRequestBuilder searchRequestBuilder = EsClient.getClient().prepareSearch(ClientNodeIndex.Index);
        searchRequestBuilder.setTypes(type);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery("timeSlice", timeSlice));
        SearchResponse response = searchRequestBuilder.execute().actionGet();

        SearchHit[] hits = response.getHits().getHits();
        logger.debug("client node list size: %s", hits.length);

        JsonArray clientNodeArray = new JsonArray();
        for (SearchHit hit : response.getHits().getHits()) {
            JsonObject clientNodeObj = new JsonObject();
            clientNodeObj.addProperty("layer", (String) hit.getSource().get("layer"));
            clientNodeObj.addProperty("component", (String) hit.getSource().get("component"));
            clientNodeObj.addProperty("serverHost", (String) hit.getSource().get("serverHost"));
            clientNodeObj.addProperty("timeSlice", (Long) hit.getSource().get("timeSlice"));
            clientNodeArray.add(clientNodeObj);
        }
        return clientNodeArray;
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long timeSlice) {
            super(sliceType, timeSlice);
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<ClientNodeSearchPersistence> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public ClientNodeSearchPersistence workerInstance(ClusterWorkerContext clusterContext) {
            return new ClientNodeSearchPersistence(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ClientNodeSearchPersistence.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
