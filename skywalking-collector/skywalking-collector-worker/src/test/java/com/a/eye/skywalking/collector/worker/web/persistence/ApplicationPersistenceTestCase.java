package com.a.eye.skywalking.collector.worker.web.persistence;

import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.index.ApplicationIndexWithDagNodeType;
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
public class ApplicationPersistenceTestCase {

    @Before
    public void initIndex() throws UnknownHostException {
        EsClient.boot();
        ApplicationIndexWithDagNodeType index = new ApplicationIndexWithDagNodeType();
        index.deleteIndex();
        index.createIndex();
        insertData();
    }

    @Test
    public void testSearchDagNode() {
        ApplicationPersistence persistence = new ApplicationPersistence();
        persistence.searchDagNode(201703101200l, 201703101259l);
    }

    private void insertData() {
        long minute = 201703101200l;
        for (int i = 0; i < 60; i++) {
            Map<String, Object> json = new HashMap<String, Object>();
            json.put("code", "DubboServer_MySQL");
            json.put("timeSlice", minute);
            json.put("component", "Dubbo");
            json.put("layer", "http");

            String _id = minute + "-DubboServer_MySQL";
            IndexResponse response = EsClient.getClient().prepareIndex(ApplicationIndexWithDagNodeType.Index, ApplicationIndexWithDagNodeType.Type, _id).setSource(json).get();
            RestStatus status = response.status();
            status.getStatus();
            minute++;
        }
    }
}
