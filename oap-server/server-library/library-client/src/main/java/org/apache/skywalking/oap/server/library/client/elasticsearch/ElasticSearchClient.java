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

package org.apache.skywalking.oap.server.library.client.elasticsearch;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.*;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.skywalking.oap.server.library.client.Client;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.action.bulk.*;
import org.elasticsearch.action.get.*;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.*;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.*;
import org.elasticsearch.client.indices.*;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.*;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ElasticSearchClient implements Client {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchClient.class);

    private static final String TYPE = "type";
    private final String clusterNodes;
    private final String namespace;
    private final String user;
    private final String password;
    private RestHighLevelClient client;

    public ElasticSearchClient(String clusterNodes, String namespace, String user, String password) {
        this.clusterNodes = clusterNodes;
        this.namespace = namespace;
        this.user = user;
        this.password = password;
    }

    @Override public void connect() {
        List<HttpHost> pairsList = parseClusterNodes(clusterNodes);
        RestClientBuilder builder;
        if (StringUtils.isNotBlank(user) && StringUtils.isNotBlank(password)) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));
            builder = RestClient.builder(pairsList.toArray(new HttpHost[0]))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        } else {
            builder = RestClient.builder(pairsList.toArray(new HttpHost[0]));
        }
        client = new RestHighLevelClient(builder);
    }

    @Override public void shutdown() {
        try {
            client.close();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private List<HttpHost> parseClusterNodes(String nodes) {
        List<HttpHost> httpHosts = new LinkedList<>();
        logger.info("elasticsearch cluster nodes: {}", nodes);
        String[] nodesSplit = nodes.split(",");
        for (String node : nodesSplit) {
            String host = node.split(":")[0];
            String port = node.split(":")[1];
            httpHosts.add(new HttpHost(host, Integer.valueOf(port)));
        }

        return httpHosts;
    }

    public boolean createIndex(String indexName, Settings settings,
        XContentBuilder mappingBuilder) throws IOException {
        indexName = formatIndexName(indexName);
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.settings(settings);
        request.mapping(mappingBuilder);
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        logger.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public boolean deleteIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        AcknowledgedResponse response = client.indices().delete(request, RequestOptions.DEFAULT);
        logger.debug("delete {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public boolean isExistsIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);
        GetIndexRequest request = new GetIndexRequest(indexName);
        return client.indices().exists(request, RequestOptions.DEFAULT);
    }

    public boolean isExistsTemplate(String indexName) throws IOException {
        indexName = formatIndexName(indexName);
        IndexTemplatesExistRequest request = new IndexTemplatesExistRequest(indexName);
        return client.indices().existsTemplate(request, RequestOptions.DEFAULT);
    }

    public boolean createTemplate(String indexName, Settings settings,
        XContentBuilder mappingBuilder) throws IOException {
        indexName = formatIndexName(indexName);

        org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest request = new PutIndexTemplateRequest(indexName);
        request.patterns(Collections.singletonList(indexName + "*"));
        request.settings(settings);
        request.mapping("_doc", mappingBuilder);

        AcknowledgedResponse response = client.indices().putTemplate(request, RequestOptions.DEFAULT);
        logger.debug("create {} template finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public boolean deleteTemplate(String indexName) throws IOException {
        indexName = formatIndexName(indexName);

        DeleteIndexTemplateRequest request = new DeleteIndexTemplateRequest();
        request.name(indexName);
        AcknowledgedResponse deleteTemplateAcknowledge = client.indices().deleteTemplate(request, RequestOptions.DEFAULT);
        return deleteTemplateAcknowledge.isAcknowledged();
    }

    public SearchResponse search(String indexName, SearchSourceBuilder searchSourceBuilder) throws IOException {
        indexName = formatIndexName(indexName);
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.types(TYPE);
        searchRequest.source(searchSourceBuilder);
        return client.search(searchRequest, RequestOptions.DEFAULT);
    }

    public GetResponse get(String indexName, String id) throws IOException {
        indexName = formatIndexName(indexName);
        GetRequest request = new GetRequest(indexName, TYPE, id);
        return client.get(request, RequestOptions.DEFAULT);
    }

    public MultiGetResponse multiGet(String indexName, List<String> ids) throws IOException {
        final String newIndexName = formatIndexName(indexName);
        MultiGetRequest request = new MultiGetRequest();
        ids.forEach(id -> request.add(newIndexName, TYPE, id));
        return client.mget(request, RequestOptions.DEFAULT);
    }

    public void forceInsert(String indexName, String id, XContentBuilder source) throws IOException {
        IndexRequest request = prepareInsert(indexName, id, source);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.index(request, RequestOptions.DEFAULT);
    }

    public void forceUpdate(String indexName, String id, XContentBuilder source, long version) throws IOException {
        UpdateRequest request = prepareUpdate(indexName, id, source);
        request.version(version);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.update(request, RequestOptions.DEFAULT);
    }

    public void forceUpdate(String indexName, String id, XContentBuilder source) throws IOException {
        UpdateRequest request = prepareUpdate(indexName, id, source);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.update(request, RequestOptions.DEFAULT);
    }

    public IndexRequest prepareInsert(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new IndexRequest(indexName, TYPE, id).source(source);
    }

    public UpdateRequest prepareUpdate(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new UpdateRequest(indexName, TYPE, id).doc(source);
    }

    public int delete(String indexName, String timeBucketColumnName, long endTimeBucket) throws IOException {
        indexName = formatIndexName(indexName);

        DeleteByQueryRequest request = new DeleteByQueryRequest();
        request.indices(indexName);
        request.setQuery(QueryBuilders.rangeQuery(timeBucketColumnName).lte(endTimeBucket));

        BulkByScrollResponse response = client.deleteByQuery(request, RequestOptions.DEFAULT);
        logger.debug("Delete data from index {}, deleted {}", indexName, response.getDeleted());
        return 200;
    }

    public String formatIndexName(String indexName) {
        if (StringUtils.isNotEmpty(namespace)) {
            return namespace + "_" + indexName;
        }
        return indexName;
    }

    public BulkProcessor createBulkProcessor(int bulkActions, int bulkSize, int flushInterval,
        int concurrentRequests) {
        BulkProcessor.Listener listener = new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId, BulkRequest request) {
                int numberOfActions = request.numberOfActions();
                logger.debug("Executing bulk [{}] with {} requests", executionId, numberOfActions);
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
                if (response.hasFailures()) {
                    logger.warn("Bulk [{}] executed with failures", executionId);
                } else {
                    logger.debug("Bulk [{}] completed in {} milliseconds",
                        executionId, response.getTook().getMillis());
                }
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                logger.error("Failed to execute bulk", failure);
            }
        };

        BiConsumer<BulkRequest, ActionListener<BulkResponse>> bulkConsumer =
            (request, bulkListener) -> client.bulkAsync(request, RequestOptions.DEFAULT, bulkListener);

        return BulkProcessor.builder(bulkConsumer, listener)
            .setBulkActions(bulkActions)
            .setBulkSize(new ByteSizeValue(bulkSize, ByteSizeUnit.MB))
            .setFlushInterval(TimeValue.timeValueSeconds(flushInterval))
            .setConcurrentRequests(concurrentRequests)
            .setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 3))
            .build();
    }
}
