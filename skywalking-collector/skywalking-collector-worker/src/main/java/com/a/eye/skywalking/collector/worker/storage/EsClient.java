package com.a.eye.skywalking.collector.worker.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author pengys5
 */
public class EsClient {

    private static Client client;

    public static void boot() throws UnknownHostException {
        Settings settings = Settings.builder()
                .put("cluster.name", "CollectorCluster")
                .put("client.transport.sniff", true)
                .build();

        client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9300));
    }

    public static Client getClient() {
        return client;
    }

    public static void indexRefresh(String... indexName) {
        Logger logger = LogManager.getFormatterLogger(EsClient.class);
        RefreshResponse response = client.admin().indices().refresh(new RefreshRequest(indexName)).actionGet();
        if (response.getShardFailures().length == response.getTotalShards()) {
            logger.error("All elasticsearch shard index refresh failure, reason: %s", response.getShardFailures());
        } else if (response.getShardFailures().length > 0) {
            logger.error("In parts of elasticsearch shard index refresh failure, reason: %s", response.getShardFailures());
        }
        logger.info("elasticsearch index refresh success");
    }
}
