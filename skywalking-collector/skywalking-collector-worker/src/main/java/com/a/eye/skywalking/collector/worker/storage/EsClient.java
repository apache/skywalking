package com.a.eye.skywalking.collector.worker.storage;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;

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

    public static TransportClient client() {
        return client;
    }
}
