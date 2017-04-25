package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public class NodeCompWithTimeSliceUseDB {

    public static void main(String[] args) throws Exception {
        EsClient.INSTANCE.boot();

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        NodeCompLoad nodeCompLoad =
            new NodeCompLoad(NodeCompLoad.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);

        JsonObject response = new JsonObject();
        nodeCompLoad.onWork(null, response);

        JsonArray nodeArray = response.get("result").getAsJsonArray();
        for (int i = 0; i < nodeArray.size(); i++) {
            JsonObject nodeJsonObj = nodeArray.get(i).getAsJsonObject();
        }
    }
}
