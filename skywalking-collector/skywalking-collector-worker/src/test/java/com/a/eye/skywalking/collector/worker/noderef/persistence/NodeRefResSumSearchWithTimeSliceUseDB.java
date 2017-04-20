package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public class NodeRefResSumSearchWithTimeSliceUseDB {

    public static void main(String[] args) throws Exception {
        EsClient.INSTANCE.boot();

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        NodeRefResSumSearchWithTimeSlice nodeRefSearch =
            new NodeRefResSumSearchWithTimeSlice(NodeRefResSumSearchWithTimeSlice.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);

        long startTime = 201703310910L;
        long endTime = 201703310920L;
        JsonObject response = new JsonObject();
        NodeRefResSumSearchWithTimeSlice.RequestEntity requestEntity =
            new NodeRefResSumSearchWithTimeSlice.RequestEntity(NodeRefIndex.TYPE_MINUTE, startTime, endTime);
        nodeRefSearch.onWork(requestEntity, response);

        JsonArray nodeRefArray = response.get("result").getAsJsonArray();
        System.out.println(nodeRefArray.toString());
        for (int i = 0; i < nodeRefArray.size(); i++) {
            JsonObject nodeRefJsonObj = nodeRefArray.get(i).getAsJsonObject();
            System.out.println(nodeRefJsonObj);
        }
    }
}
