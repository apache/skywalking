package com.a.eye.skywalking.collector.worker.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static Logger logger = LogManager.getFormatterLogger(EsClient.class);

    private static Client client;

    public static void boot() throws UnknownHostException {
        Settings settings = Settings.builder()
                .put("cluster.name", "CollectorCluster")
                .put("client.transport.sniff", true).build();

        client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9300));
    }

    public static Client getClient() {
        return client;
    }
}
