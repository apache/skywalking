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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.client;

import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.action.bulk.BackoffPolicy;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.IndexTemplatesExistRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peng-yongsheng
 * @author kezhenxu94
 */
public class ElasticSearch7Client extends ElasticSearchClient {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearch7Client.class);

    public ElasticSearch7Client(final String clusterNodes,
                                final String protocol,
                                final String trustStorePath,
                                final String trustStorePass,
                                final String namespace, final String user, final String password) {
        super(clusterNodes, protocol, trustStorePath, trustStorePass, namespace, user, password);
    }

    @Override
    public void connect() throws IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, CertificateException {
        List<HttpHost> hosts = parseClusterNodes(protocol, clusterNodes);
        client = createClient(hosts);
        client.ping(RequestOptions.DEFAULT);
    }

    public boolean createIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);

        CreateIndexRequest request = new CreateIndexRequest(indexName);
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        logger.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public boolean createIndex(String indexName,
                               Map<String, Object> settings,
                               Map<String, Object> mapping) throws IOException {
        indexName = formatIndexName(indexName);
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.settings(settings);
        request.mapping(mapping);
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        logger.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> retrievalIndexByAliases(String aliases) throws IOException {
        aliases = formatIndexName(aliases);

        GetAliasesRequest getAliasesRequest = new GetAliasesRequest(aliases);
        GetAliasesResponse alias = client.indices().getAlias(getAliasesRequest, RequestOptions.DEFAULT);
        return new ArrayList<>(alias.getAliases().keySet());
    }

    protected boolean deleteIndex(String indexName, boolean formatIndexName) throws IOException {
        if (formatIndexName) {
            indexName = formatIndexName(indexName);
        }
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

        IndexTemplatesExistRequest indexTemplatesExistRequest = new IndexTemplatesExistRequest(indexName);

        return client.indices().existsTemplate(indexTemplatesExistRequest, RequestOptions.DEFAULT);
    }

    public boolean createTemplate(String indexName,
                                  Map<String, Object> settings,
                                  Map<String, Object> mapping) throws IOException {
        indexName = formatIndexName(indexName);

        PutIndexTemplateRequest putIndexTemplateRequest =
            new PutIndexTemplateRequest(indexName)
                .patterns(Collections.singletonList(indexName + "-*"))
                .alias(new Alias(indexName))
                .settings(settings)
                .mapping(mapping);

        AcknowledgedResponse acknowledgedResponse =
            client.indices().putTemplate(putIndexTemplateRequest, RequestOptions.DEFAULT);

        return acknowledgedResponse.isAcknowledged();
    }

    public boolean deleteTemplate(String indexName) throws IOException {
        indexName = formatIndexName(indexName);

        DeleteIndexTemplateRequest deleteIndexTemplateRequest = new DeleteIndexTemplateRequest(indexName);
        AcknowledgedResponse acknowledgedResponse = client.indices().deleteTemplate(deleteIndexTemplateRequest, RequestOptions.DEFAULT);

        return acknowledgedResponse.isAcknowledged();
    }

    public SearchResponse search(
        String indexName,
        SearchSourceBuilder searchSourceBuilder) throws IOException {
        indexName = formatIndexName(indexName);
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source(searchSourceBuilder);
        return client.search(searchRequest, RequestOptions.DEFAULT);
    }

    public GetResponse get(String indexName, String id) throws IOException {
        indexName = formatIndexName(indexName);
        GetRequest request = new GetRequest(indexName, id);
        return client.get(request, RequestOptions.DEFAULT);
    }

    public SearchResponse ids(
        String indexName,
        String[] ids) throws IOException {
        indexName = formatIndexName(indexName);

        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source().query(QueryBuilders.idsQuery().addIds(ids)).size(ids.length);
        return client.search(searchRequest, RequestOptions.DEFAULT);
    }

    public void forceInsert(String indexName, String id, XContentBuilder source) throws IOException {
        IndexRequest request = (IndexRequest) prepareInsert(indexName, id, source);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.index(request, RequestOptions.DEFAULT);
    }

    public void forceUpdate(String indexName,
                            String id,
                            XContentBuilder source,
                            long seqNo,
                            long primaryTerm) throws IOException {
        org.elasticsearch.action.update.UpdateRequest request = (org.elasticsearch.action.update.UpdateRequest) prepareUpdate(indexName, id, source);
        request.setIfSeqNo(seqNo);
        request.setIfPrimaryTerm(primaryTerm);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.update(request, RequestOptions.DEFAULT);
    }

    public void forceUpdate(String indexName, String id, XContentBuilder source) throws IOException {
        org.elasticsearch.action.update.UpdateRequest request = (org.elasticsearch.action.update.UpdateRequest) prepareUpdate(indexName, id, source);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.update(request, RequestOptions.DEFAULT);
    }

    public InsertRequest prepareInsert(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new ElasticSearch7InsertRequest(indexName, id).source(source);
    }

    public UpdateRequest prepareUpdate(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new ElasticSearch7UpdateRequest(indexName, id).doc(source);
    }

    public int delete(
        String indexName,
        String timeBucketColumnName,
        long endTimeBucket) throws IOException {
        indexName = formatIndexName(indexName);

        DeleteByQueryRequest deleteByQueryRequest = new DeleteByQueryRequest(indexName);
        deleteByQueryRequest.setAbortOnVersionConflict(false);
        deleteByQueryRequest.setQuery(
            QueryBuilders.rangeQuery(timeBucketColumnName).lte(endTimeBucket)
        );
        BulkByScrollResponse bulkByScrollResponse = client.deleteByQuery(deleteByQueryRequest, RequestOptions.DEFAULT);
        logger.debug(
            "delete indexName: {}, by query request: {}, response: {}",
            indexName, deleteByQueryRequest, bulkByScrollResponse
        );
        return HttpStatus.SC_OK;
    }

    public void synchronousBulk(BulkRequest request) {
        request.timeout(TimeValue.timeValueMinutes(2));
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
        request.waitForActiveShards(ActiveShardCount.ONE);
        try {
            int size = request.requests().size();
            BulkResponse responses = client.bulk(request, RequestOptions.DEFAULT);
            logger.info("Synchronous bulk took time: {} millis, size: {}", responses.getTook().getMillis(), size);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public BulkProcessor createBulkProcessor(int bulkActions, int flushInterval, int concurrentRequests) {
        BulkProcessor.Listener listener = createBulkListener();

        return BulkProcessor.builder(
            (bulkRequest, bulkResponseActionListener) -> client.bulkAsync(bulkRequest, RequestOptions.DEFAULT, bulkResponseActionListener), listener)
            .setBulkActions(bulkActions)
            .setFlushInterval(TimeValue.timeValueSeconds(flushInterval))
            .setConcurrentRequests(concurrentRequests)
            .setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 3))
            .build();
    }
}
