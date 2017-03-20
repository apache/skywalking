package com.a.eye.skywalking.collector.worker.dagnode.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.TimeSlice;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.node.NodeIndex;
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
public class ServerNodeSearchPersistence extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(ServerNodeSearchPersistence.class);

    public ServerNodeSearchPersistence(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
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
        SearchRequestBuilder searchRequestBuilder = EsClient.getClient().prepareSearch(NodeIndex.Index);
        searchRequestBuilder.setTypes(type);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.termQuery("timeSlice", timeSlice));
        SearchResponse response = searchRequestBuilder.execute().actionGet();

        SearchHit[] hits = response.getHits().getHits();
        logger.debug("server node list size: %s", hits.length);

        JsonArray serverNodeArray = new JsonArray();
        for (SearchHit hit : response.getHits().getHits()) {
            JsonObject serverNodeObj = new JsonObject();
            serverNodeObj.addProperty("code", (String) hit.getSource().get("code"));
            serverNodeObj.addProperty("component", (String) hit.getSource().get("component"));
            serverNodeObj.addProperty("layer", (String) hit.getSource().get("layer"));
            serverNodeObj.addProperty("timeSlice", (Long) hit.getSource().get("timeSlice"));
            serverNodeArray.add(serverNodeObj);
        }
        return serverNodeArray;
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long timeSlice) {
            super(sliceType, timeSlice);
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<ServerNodeSearchPersistence> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public ServerNodeSearchPersistence workerInstance(ClusterWorkerContext clusterContext) {
            return new ServerNodeSearchPersistence(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return ServerNodeSearchPersistence.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
