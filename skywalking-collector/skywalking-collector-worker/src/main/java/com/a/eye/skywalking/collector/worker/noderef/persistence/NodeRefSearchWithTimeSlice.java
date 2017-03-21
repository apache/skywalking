package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
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
import org.elasticsearch.search.SearchHit;

/**
 * @author pengys5
 */
public class NodeRefSearchWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeRefSearchWithTimeSlice.class);

    private NodeRefSearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;

            SearchRequestBuilder searchRequestBuilder = EsClient.getClient().prepareSearch(NodeRefIndex.Index);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.termQuery(NodeRefIndex.Time_Slice, search.getTimeSlice()));
            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            SearchHit[] hits = searchResponse.getHits().getHits();
            logger.debug("node reference list size: %s", hits.length);

            JsonArray nodeRefArray = new JsonArray();
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                JsonObject nodeRefObj = new JsonObject();
                nodeRefObj.addProperty(NodeRefIndex.Front, (String) hit.getSource().get(NodeRefIndex.Front));
                nodeRefObj.addProperty(NodeRefIndex.FrontIsRealCode, (Boolean) hit.getSource().get(NodeRefIndex.FrontIsRealCode));
                nodeRefObj.addProperty(NodeRefIndex.Behind, (String) hit.getSource().get(NodeRefIndex.Behind));
                nodeRefObj.addProperty(NodeRefIndex.BehindIsRealCode, (Boolean) hit.getSource().get(NodeRefIndex.BehindIsRealCode));
                nodeRefObj.addProperty(NodeRefIndex.Time_Slice, (Long) hit.getSource().get(NodeRefIndex.Time_Slice));
                nodeRefArray.add(nodeRefObj);
            }

            JsonObject resJsonObj = (JsonObject) response;
            resJsonObj.add("result", nodeRefArray);
        } else {
            throw new IllegalArgumentException("message instance must be RequestEntity");
        }
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long timeSlice) {
            super(sliceType, timeSlice);
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
