package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.node.NodeMappingIndex;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public class NodeMappingSearchWithTimeSliceUseDB {

    public static void main(String[] args) throws Exception {
        EsClient.INSTANCE.boot();

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        NodeMappingSearchWithTimeSlice nodeMappingSearch =
            new NodeMappingSearchWithTimeSlice(NodeMappingSearchWithTimeSlice.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);

        long startTime = 201703310910L;
        long endTime = 201703310920L;
        JsonObject response = new JsonObject();
        NodeMappingSearchWithTimeSlice.RequestEntity requestEntity =
            new NodeMappingSearchWithTimeSlice.RequestEntity(NodeMappingIndex.TYPE_MINUTE, startTime, endTime);
        nodeMappingSearch.onWork(requestEntity, response);

        JsonArray nodeArray = response.get("result").getAsJsonArray();
        for (int i = 0; i < nodeArray.size(); i++) {
            JsonObject nodeJsonObj = nodeArray.get(i).getAsJsonObject();
        }
    }
}
