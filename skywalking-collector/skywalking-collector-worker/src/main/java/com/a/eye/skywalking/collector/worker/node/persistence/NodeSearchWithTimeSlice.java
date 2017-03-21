package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.TimeSlice;
import com.a.eye.skywalking.collector.worker.node.NodeIndex;
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
public class NodeSearchWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeSearchWithTimeSlice.class);

    private NodeSearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;

            SearchRequestBuilder searchRequestBuilder = EsClient.getClient().prepareSearch(NodeIndex.Index);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.termQuery(NodeIndex.Time_Slice, search.getTimeSlice()));
            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            SearchHit[] hits = searchResponse.getHits().getHits();
            logger.debug("server node list size: %s", hits.length);

            JsonArray nodeArray = new JsonArray();
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                JsonObject nodeObj = new JsonObject();
                nodeObj.addProperty(NodeIndex.Code, (String) hit.getSource().get(NodeIndex.Code));
                nodeObj.addProperty(NodeIndex.Component, (String) hit.getSource().get(NodeIndex.Component));
                nodeObj.addProperty(NodeIndex.Layer, (String) hit.getSource().get(NodeIndex.Layer));
                nodeObj.addProperty(NodeIndex.Kind, (String) hit.getSource().get(NodeIndex.Kind));
                nodeObj.addProperty(NodeIndex.NickName, (String) hit.getSource().get(NodeIndex.NickName));
                nodeObj.addProperty(NodeIndex.Time_Slice, (Long) hit.getSource().get(NodeIndex.Time_Slice));
                nodeArray.add(nodeObj);
            }

            JsonObject resJsonObj = (JsonObject) response;
            resJsonObj.add("result", nodeArray);
        } else {
            throw new IllegalArgumentException("message instance must be RequestEntity");
        }
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long timeSlice) {
            super(sliceType, timeSlice);
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeSearchWithTimeSlice> {
        public static Factory INSTANCE = new Factory();

        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeSearchWithTimeSlice workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeSearchWithTimeSlice(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeSearchWithTimeSlice.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
