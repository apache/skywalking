package com.a.eye.skywalking.collector.worker.storage;

import com.google.gson.JsonObject;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * @author pengys5
 */
public class EsClient {

    private static TransportClient client;

    public void boot() throws UnknownHostException {
        Settings settings = Settings.builder()
                .put("cluster.name", "myClusterName").build();

        client = new PreBuiltTransportClient(Settings.EMPTY)
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("host1"), 9300))
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("host1"), 9300));
    }

    public static boolean saveToEs(String esIndex, String esType, Map<String, JsonObject> persistenceData) {
        BulkRequestBuilder bulkRequest = client.prepareBulk();

        for (Map.Entry<String, JsonObject> entry : persistenceData.entrySet()) {
            String id = entry.getKey();
            JsonObject data = entry.getValue();
            bulkRequest.add(client.prepareIndex(esIndex, esType, id).setSource(data.toString()));
        }

        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        return !bulkResponse.hasFailures();
    }
}
