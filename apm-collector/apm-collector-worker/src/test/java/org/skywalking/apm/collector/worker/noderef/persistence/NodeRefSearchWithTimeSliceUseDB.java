package org.skywalking.apm.collector.worker.noderef.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.worker.noderef.NodeRefIndex;
import org.skywalking.apm.collector.worker.storage.EsClient;

/**
 * @author pengys5
 */
public class NodeRefSearchWithTimeSliceUseDB {

    public static void main(String[] args) throws Exception {
        EsClient.INSTANCE.boot();

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        NodeRefSearchWithTimeSlice nodeRefSearch =
            new NodeRefSearchWithTimeSlice(NodeRefSearchWithTimeSlice.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);

        long startTime = 201703310910L;
        long endTime = 201703310920L;
        JsonObject response = new JsonObject();
        NodeRefSearchWithTimeSlice.RequestEntity requestEntity =
            new NodeRefSearchWithTimeSlice.RequestEntity(NodeRefIndex.TYPE_MINUTE, startTime, endTime);
        nodeRefSearch.onWork(requestEntity, response);

        JsonArray nodeRefArray = response.get("result").getAsJsonArray();
        for (int i = 0; i < nodeRefArray.size(); i++) {
            JsonObject nodeRefJsonObj = nodeRefArray.get(i).getAsJsonObject();
        }
    }
}
