package com.a.eye.skywalking.collector.worker.dagnode.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.LocalSyncWorkerRef;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.index.ClientNodeIndex;
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
public class ClientNodeTestCase {

    @Before
    public void initIndex() throws UnknownHostException {
        EsClient.boot();
        ClientNodeIndex index = new ClientNodeIndex();
        index.deleteIndex();
        index.createIndex();
    }

    @Test
    public void testLoadClientNode() throws Exception {
        loadNode(201703101201l, ClientNodeIndex.Type_Minute);
        loadNode(201703101200l, ClientNodeIndex.Type_Hour);
        loadNode(201703100000l, ClientNodeIndex.Type_Day);
    }

    public void loadNode(long timeSlice, String type) throws Exception {
        LocalSyncWorkerRef workerRef = (LocalSyncWorkerRef) ClientNodeSearchPersistence.Factory.INSTANCE.create(AbstractWorker.noOwner());

        insertData(timeSlice, type);
        EsClient.indexRefresh(ClientNodeIndex.Index);

        ClientNodeSearchPersistence.RequestEntity requestEntity = new ClientNodeSearchPersistence.RequestEntity(type, timeSlice);
        JsonObject resJsonObj = new JsonObject();
        workerRef.ask(requestEntity, resJsonObj);
        JsonArray nodeArray = resJsonObj.get("result").getAsJsonArray();
        for (int i = 0; i < nodeArray.size(); i++) {
            JsonObject node = nodeArray.get(i).getAsJsonObject();
            System.out.println(node);
        }
    }

    private void insertData(long timeSlice, String type) {
        Map<String, Object> json = new HashMap<String, Object>();
        json.put("serverHost", "10.20.3.15:3000");
        json.put("timeSlice", timeSlice);
        json.put("component", "Motan");
        json.put("layer", "rpc");

        String _id = timeSlice + "-Motan-10.20.3.15:3000";
        IndexResponse response = EsClient.getClient().prepareIndex(ClientNodeIndex.Index, type, _id).setSource(json).get();
        RestStatus status = response.status();
        status.getStatus();

        json = new HashMap<String, Object>();
        json.put("serverHost", "10.5.34.18");
        json.put("timeSlice", timeSlice);
        json.put("component", "Mysql");
        json.put("layer", "db");
        _id = timeSlice + "-Mysql-10.5.34.18";

        response = EsClient.getClient().prepareIndex(ClientNodeIndex.Index, type, _id).setSource(json).get();
        status = response.status();
        status.getStatus();
    }
}
