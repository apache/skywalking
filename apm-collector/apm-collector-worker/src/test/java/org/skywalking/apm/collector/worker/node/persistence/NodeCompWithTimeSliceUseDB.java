package org.skywalking.apm.collector.worker.node.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.worker.storage.EsClient;

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
