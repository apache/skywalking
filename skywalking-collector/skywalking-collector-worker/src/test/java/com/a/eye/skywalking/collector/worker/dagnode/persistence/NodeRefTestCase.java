package com.a.eye.skywalking.collector.worker.dagnode.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.LocalSyncWorkerRef;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.rest.RestStatus;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class NodeRefTestCase {

//    @Before
    public void initIndex() throws UnknownHostException {
        EsClient.boot();
        NodeRefIndex index = new NodeRefIndex();
        index.deleteIndex();
        index.createIndex();
    }

//    @Test
    public void testLoadNodeRef() throws Exception {
        loadNodeRef(201703101201l, NodeRefIndex.Type_Minute);
        loadNodeRef(201703101200l, NodeRefIndex.Type_Hour);
        loadNodeRef(201703100000l, NodeRefIndex.Type_Day);
    }

    public void loadNodeRef(long timeSlice, String type) throws Exception {
//        LocalSyncWorkerRef workerRef = (LocalSyncWorkerRef) NodeRefSearchPersistence.Factory.INSTANCE.create(AbstractWorker.noOwner());
//
//        insertData(timeSlice, type);
//        EsClient.indexRefresh(NodeRefIndex.Index);
//
//        NodeRefSearchPersistence.RequestEntity requestEntity = new NodeRefSearchPersistence.RequestEntity(type, timeSlice);
//        JsonObject resJsonObj = new JsonObject();
//        workerRef.ask(requestEntity, resJsonObj);
//        JsonArray nodeArray = resJsonObj.get("result").getAsJsonArray();
//        for (int i = 0; i < nodeArray.size(); i++) {
//            JsonObject node = nodeArray.get(i).getAsJsonObject();
//            System.out.println(node);
//        }
    }

    private void insertData(long timeSlice, String type) {
        Map<String, Object> json = new HashMap<String, Object>();
        json.put("client", "WebApplication");
        json.put("server", "MotanServiceApplication");
        json.put("timeSlice", timeSlice);

        String _id = timeSlice + "-WebApplication-MotanServiceApplication";
        IndexResponse response = EsClient.getClient().prepareIndex(NodeRefIndex.Index, type, _id).setSource(json).get();
        RestStatus status = response.status();
        status.getStatus();
    }
}
