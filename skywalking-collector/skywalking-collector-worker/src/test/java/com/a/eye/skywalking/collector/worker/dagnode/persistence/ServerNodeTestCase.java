package com.a.eye.skywalking.collector.worker.dagnode.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.LocalSyncWorkerRef;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.node.NodeIndex;
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
public class ServerNodeTestCase {

    @Before
    public void initIndex() throws UnknownHostException {
        EsClient.boot();
        NodeIndex index = new NodeIndex();
        index.deleteIndex();
        index.createIndex();
    }

    @Test
    public void testLoadServerNode() throws Exception {
        loadNode(201703101201l, NodeIndex.Type_Minute);
        loadNode(201703101200l, NodeIndex.Type_Hour);
        loadNode(201703100000l, NodeIndex.Type_Day);
    }

    public void loadNode(long timeSlice, String type) throws Exception {
//        LocalSyncWorkerRef workerRef = (LocalSyncWorkerRef) ServerNodeSearchPersistence.Factory.INSTANCE.create(AbstractWorker.noOwner());
//
//        insertData(timeSlice, type);
//        EsClient.indexRefresh(NodeIndex.Index);
//
//        ServerNodeSearchPersistence.RequestEntity requestEntity = new ServerNodeSearchPersistence.RequestEntity(type, timeSlice);
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
        json.put("code", "WebApplication");
        json.put("timeSlice", timeSlice);
        json.put("component", "Tomcat");
        json.put("layer", "http");

        String _id = timeSlice + "-WebApplication";
        IndexResponse response = EsClient.getClient().prepareIndex(NodeIndex.Index, type, _id).setSource(json).get();
        RestStatus status = response.status();
        status.getStatus();

        json = new HashMap<String, Object>();
        json.put("code", "MotanServiceApplication");
        json.put("timeSlice", timeSlice);
        json.put("component", "Motan");
        json.put("layer", "rpc");
        _id = timeSlice + "-MotanServiceApplication";

        response = EsClient.getClient().prepareIndex(NodeIndex.Index, type, _id).setSource(json).get();
        status = response.status();
        status.getStatus();
    }
}
