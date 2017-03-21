package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
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
import org.elasticsearch.search.SearchHit;

/**
 * @author pengys5
 */
public class NodeRefResSumSearchWithTimeSlice extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeRefResSumSearchWithTimeSlice.class);

    private NodeRefResSumSearchWithTimeSlice(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        if (request instanceof RequestEntity) {
            RequestEntity search = (RequestEntity) request;

            SearchRequestBuilder searchRequestBuilder = EsClient.getClient().prepareSearch(NodeRefResSumIndex.Index);
            searchRequestBuilder.setTypes(search.getSliceType());
            searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
            searchRequestBuilder.setQuery(QueryBuilders.termQuery(NodeRefResSumIndex.Time_Slice, search.getTimeSlice()));
            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

            SearchHit[] hits = searchResponse.getHits().getHits();
            logger.debug("node reference list size: %s", hits.length);

            JsonArray nodeRefResSumArray = new JsonArray();
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                JsonObject nodeRefResSumObj = new JsonObject();
                String id = hit.getId();
                String[] ids = id.split("-");
                String front = ids[1];
                String behind = ids[2];

                nodeRefResSumObj.addProperty("front", front);
                nodeRefResSumObj.addProperty("behind", behind);

                nodeRefResSumObj.addProperty(NodeRefResSumIndex.OneSecondLess, (Number) hit.getSource().get(NodeRefResSumIndex.OneSecondLess));
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.ThreeSecondLess, (Number) hit.getSource().get(NodeRefResSumIndex.ThreeSecondLess));
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.FiveSecondLess, (Number) hit.getSource().get(NodeRefResSumIndex.FiveSecondLess));
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.FiveSecondGreater, (Number) hit.getSource().get(NodeRefResSumIndex.FiveSecondGreater));
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.Error, (Number) hit.getSource().get(NodeRefResSumIndex.Error));
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.Summary, (Number) hit.getSource().get(NodeRefResSumIndex.Summary));
                nodeRefResSumObj.addProperty(NodeRefResSumIndex.Time_Slice, (Long) hit.getSource().get(NodeRefResSumIndex.Time_Slice));
                nodeRefResSumArray.add(nodeRefResSumObj);
            }

            JsonObject resJsonObj = (JsonObject) response;
            resJsonObj.add("result", nodeRefResSumArray);
        } else {
            throw new IllegalArgumentException("message instance must be RequestEntity");
        }
    }

    public static class RequestEntity extends TimeSlice {
        public RequestEntity(String sliceType, long timeSlice) {
            super(sliceType, timeSlice);
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
