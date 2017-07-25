package org.skywalking.apm.collector.client.elasticsearch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ElasticSearchClient implements Client {

    private final Logger logger = LoggerFactory.getLogger(ElasticSearchClient.class);

    private org.elasticsearch.client.Client client;

    private final String clusterName;

    private final Boolean clusterTransportSniffer;

    private final String clusterNodes;

    public ElasticSearchClient(String clusterName, Boolean clusterTransportSniffer, String clusterNodes) {
        this.clusterName = clusterName;
        this.clusterTransportSniffer = clusterTransportSniffer;
        this.clusterNodes = clusterNodes;
    }

    @Override public void initialize() throws ClientException {
        Settings settings = Settings.builder()
            .put("cluster.name", clusterName)
            .put("client.transport.sniff", clusterTransportSniffer)
            .build();

        client = new PreBuiltTransportClient(settings);

        List<AddressPairs> pairsList = parseClusterNodes(clusterNodes);
        for (AddressPairs pairs : pairsList) {
            try {
                ((PreBuiltTransportClient)client).addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(pairs.host), pairs.port));
            } catch (UnknownHostException e) {
                throw new ElasticSearchClientException(e.getMessage(), e);
            }
        }
    }

    private List<AddressPairs> parseClusterNodes(String nodes) {
        List<AddressPairs> pairsList = new LinkedList<>();
        logger.info("elasticsearch cluster nodes: {}", nodes);
        String[] nodesSplit = nodes.split(",");
        for (int i = 0; i < nodesSplit.length; i++) {
            String node = nodesSplit[i];
            String host = node.split(":")[0];
            String port = node.split(":")[1];
            pairsList.add(new AddressPairs(host, Integer.valueOf(port)));
        }

        return pairsList;
    }

    class AddressPairs {
        private String host;
        private Integer port;

        public AddressPairs(String host, Integer port) {
            this.host = host;
            this.port = port;
        }
    }

    public boolean createIndex(String indexName, String indexType, Settings settings, XContentBuilder mappingBuilder) {
        IndicesAdminClient adminClient = client.admin().indices();
        CreateIndexResponse response = adminClient.prepareCreate(indexName).setSettings(settings).addMapping(indexType, mappingBuilder).get();
        logger.info("create {} index with type of {} finished, isAcknowledged: {}", indexName, indexType, response.isAcknowledged());
        return response.isShardsAcked();
    }

    public boolean deleteIndex(String indexName) {
        IndicesAdminClient adminClient = client.admin().indices();
        DeleteIndexResponse response = adminClient.prepareDelete(indexName).get();
        logger.info("delete {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public boolean isExistsIndex(String indexName) {
        IndicesAdminClient adminClient = client.admin().indices();
        IndicesExistsResponse response = adminClient.prepareExists(indexName).get();
        return response.isExists();
    }

    public SearchRequestBuilder prepareSearch(String indexName) {
        return client.prepareSearch(indexName);
    }

    public IndexRequestBuilder prepareIndex(String indexName) {
        return null;
    }
}
