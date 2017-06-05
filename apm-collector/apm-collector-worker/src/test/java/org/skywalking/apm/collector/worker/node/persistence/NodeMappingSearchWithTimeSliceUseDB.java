package org.skywalking.apm.collector.worker.node.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.worker.node.NodeMappingIndex;
import org.skywalking.apm.collector.worker.storage.EsClient;

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
