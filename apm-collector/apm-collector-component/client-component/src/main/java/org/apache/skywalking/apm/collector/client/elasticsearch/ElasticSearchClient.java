/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.skywalking.apm.collector.client.elasticsearch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.skywalking.apm.collector.client.ClientException;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.DeleteByQueryRequestBuilder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.apache.skywalking.apm.collector.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
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

    @Override public void shutdown() {

    }

    private List<AddressPairs> parseClusterNodes(String nodes) {
        List<AddressPairs> pairsList = new LinkedList<>();
        logger.info("elasticsearch cluster nodes: {}", nodes);
        String[] nodesSplit = nodes.split(",");
        for (String node : nodesSplit) {
            String host = node.split(":")[0];
            String port = node.split(":")[1];
            pairsList.add(new AddressPairs(host, Integer.valueOf(port)));
        }

        return pairsList;
    }

    class AddressPairs {
        private String host;
        private Integer port;

        AddressPairs(String host, Integer port) {
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

    public IndexRequestBuilder prepareIndex(String indexName, String id) {
        return client.prepareIndex(indexName, "type", id);
    }

    public UpdateRequestBuilder prepareUpdate(String indexName, String id) {
        return client.prepareUpdate(indexName, "type", id);
    }

    public GetRequestBuilder prepareGet(String indexName, String id) {
        return client.prepareGet(indexName, "type", id);
    }

    public DeleteByQueryRequestBuilder prepareDelete() {
        return DeleteByQueryAction.INSTANCE.newRequestBuilder(client);
    }

    public MultiGetRequestBuilder prepareMultiGet() {
        return client.prepareMultiGet();
    }

    public BulkRequestBuilder prepareBulk() {
        return client.prepareBulk();
    }

    public void update(UpdateRequest updateRequest) {
        try {
            client.update(updateRequest).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
