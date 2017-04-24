package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.config.EsConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author pengys5
 */
public enum EsClient {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(EsClient.class);

    private Client client;

    public void boot() throws UnknownHostException {
        Settings settings = Settings.builder()
            .put("cluster.name", EsConfig.Es.Cluster.NAME)
            .put("client.transport.sniff", EsConfig.Es.Cluster.Transport.SNIFFER)
            .build();

        client = new PreBuiltTransportClient(settings);

        List<AddressPairs> pairsList = parseClusterNodes(EsConfig.Es.Cluster.NODES);
        for (AddressPairs pairs : pairsList) {
            ((PreBuiltTransportClient)client).addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(pairs.ip), pairs.port));
        }
    }

    public Client getClient() {
        return client;
    }

    public void indexRefresh(String... indexName) {
        RefreshResponse response = client.admin().indices().refresh(new RefreshRequest(indexName)).actionGet();
        if (response.getShardFailures().length == response.getTotalShards()) {
            logger.error("All elasticsearch shard index refresh failure, reason: %s", Arrays.toString(response.getShardFailures()));
        } else if (response.getShardFailures().length > 0) {
            logger.error("In parts of elasticsearch shard index refresh failure, reason: %s", Arrays.toString(response.getShardFailures()));
        }
        logger.info("elasticsearch index refresh success");
    }

    private List<AddressPairs> parseClusterNodes(String nodes) {
        List<AddressPairs> pairsList = new ArrayList<>();
        logger.info("es NODES: %s", nodes);
        String[] nodesSplit = nodes.split(",");
        for (int i = 0; i < nodesSplit.length; i++) {
            String node = nodesSplit[i];
            String ip = node.split(":")[0];
            String port = node.split(":")[1];
            pairsList.add(new AddressPairs(ip, Integer.valueOf(port)));
        }

        return pairsList;
    }

    class AddressPairs {
        private String ip;
        private Integer port;

        public AddressPairs(String ip, Integer port) {
            this.ip = ip;
            this.port = port;
        }
    }

    public void bulk(List<IndexRequestBuilder> dataList){
        Client client = EsClient.INSTANCE.getClient();
        BulkRequestBuilder bulkRequest = client.prepareBulk();

        logger.info("bulk data size: %s", dataList.size());
        if (dataList.size() > 0) {
            for (IndexRequestBuilder builder : dataList) {
                bulkRequest.add(builder);
            }

            BulkResponse bulkResponse = bulkRequest.execute().actionGet();
            if (bulkResponse.hasFailures()) {
                logger.error(bulkResponse.buildFailureMessage());
            }
        }
    }
}
