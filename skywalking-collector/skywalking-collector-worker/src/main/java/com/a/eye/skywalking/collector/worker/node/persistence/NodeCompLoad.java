package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.node.NodeCompIndex;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.search.SearchHit;

/**
 * @author pengys5
 */
public class NodeCompLoad extends AbstractLocalSyncWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeCompLoad.class);

    NodeCompLoad(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void onWork(Object request, Object response) throws Exception {
        SearchRequestBuilder searchRequestBuilder = EsClient.INSTANCE.getClient().prepareSearch(NodeCompIndex.INDEX);
        searchRequestBuilder.setTypes(NodeCompIndex.TYPE_RECORD);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setSize(100);
        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        SearchHit[] searchHits = searchResponse.getHits().getHits();

        JsonArray nodeCompArray = new JsonArray();
        for (SearchHit searchHit : searchHits) {
            JsonObject nodeCompObj = new JsonObject();
            nodeCompObj.addProperty(NodeCompIndex.NAME, (String)searchHit.getSource().get(NodeCompIndex.NAME));
            nodeCompObj.addProperty(NodeCompIndex.PEERS, (String)searchHit.getSource().get(NodeCompIndex.PEERS));
            nodeCompArray.add(nodeCompObj);
            logger.debug("node: %s", nodeCompObj.toString());
        }

        JsonObject resJsonObj = (JsonObject)response;
        resJsonObj.add("result", nodeCompArray);
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<NodeCompLoad> {
        @Override
        public Role role() {
            return WorkerRole.INSTANCE;
        }

        @Override
        public NodeCompLoad workerInstance(ClusterWorkerContext clusterContext) {
            return new NodeCompLoad(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return NodeCompLoad.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
