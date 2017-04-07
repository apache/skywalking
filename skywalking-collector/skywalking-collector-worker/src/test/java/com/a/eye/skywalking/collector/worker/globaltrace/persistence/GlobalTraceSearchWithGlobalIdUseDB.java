package com.a.eye.skywalking.collector.worker.globaltrace.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.worker.node.NodeMappingIndex;
import com.a.eye.skywalking.collector.worker.node.persistence.NodeMappingSearchWithTimeSlice;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public class GlobalTraceSearchWithGlobalIdUseDB {

    public static void main(String[] args) throws Exception {
        EsClient.INSTANCE.boot();

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        GlobalTraceSearchWithGlobalId globalTraceSearchWithGlobalId =
                new GlobalTraceSearchWithGlobalId(GlobalTraceSearchWithGlobalId.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);

        JsonObject response = new JsonObject();
        globalTraceSearchWithGlobalId.onWork("Trace.1491277147443.-1562443425.70539.65.2", response);

        JsonArray nodeArray = response.get("result").getAsJsonArray();
        System.out.println(nodeArray.size());
        System.out.println(nodeArray.toString());
        for (int i = 0; i < nodeArray.size(); i++) {
            JsonObject nodeJsonObj = nodeArray.get(i).getAsJsonObject();
            System.out.println(nodeJsonObj);
        }
    }
}
