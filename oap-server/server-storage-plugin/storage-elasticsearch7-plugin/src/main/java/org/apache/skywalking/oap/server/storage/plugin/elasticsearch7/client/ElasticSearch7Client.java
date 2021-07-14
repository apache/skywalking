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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.IndexNameConverter;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
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
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.IndexTemplatesExistRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.builder.SearchSourceBuilder;

@Slf4j
public class ElasticSearch7Client extends ElasticSearchClient {
    public ElasticSearch7Client(final String clusterNodes,
                                final String protocol,
                                final String trustStorePath,
                                final String trustStorePass,
                                final String user,
                                final String password,
                                List<IndexNameConverter> indexNameConverters,
                                int connectTimeout,
                                int socketTimeout) {
        super(
            clusterNodes, protocol, trustStorePath, trustStorePass, user, password,
            indexNameConverters, connectTimeout, socketTimeout
        );
    }

    @Override
    public void connect() throws IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, CertificateException {
        connectLock.lock();
        try {
            if (client != null) {
                try {
                    client.close();
                } catch (Throwable t) {
                    log.error("ElasticSearch7 client reconnection fails based on new config", t);
                }
            }
            List<HttpHost> hosts = parseClusterNodes(protocol, clusterNodes);
            client = createClient(hosts);
            client.ping(RequestOptions.DEFAULT);
        } finally {
            connectLock.unlock();
        }
    }

    @Override
    public boolean createIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);

        CreateIndexRequest request = new CreateIndexRequest(indexName);
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        log.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    @Override
    public boolean createIndex(String indexName, Map<String, Object> settings,
                               Map<String, Object> mapping) throws IOException {
        indexName = formatIndexName(indexName);
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.settings(settings);
        request.mapping(mapping);
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        log.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> retrievalIndexByAliases(String aliases) throws IOException {
        aliases = formatIndexName(aliases);

        GetAliasesRequest getAliasesRequest = new GetAliasesRequest(aliases);
        GetAliasesResponse alias;
        try {
            alias = client.indices().getAlias(getAliasesRequest, RequestOptions.DEFAULT);
            healthChecker.health();
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
        return new ArrayList<>(alias.getAliases().keySet());
    }

    @Override
    protected boolean deleteIndex(String indexName, boolean formatIndexName) throws IOException {
        if (formatIndexName) {
            indexName = formatIndexName(indexName);
        }
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        AcknowledgedResponse response = client.indices().delete(request, RequestOptions.DEFAULT);
        log.debug("delete {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    @Override
    public Map<String, Object> getIndex(String indexName) throws IOException {
        if (StringUtil.isBlank(indexName)) {
            return new HashMap<>();
        }
        indexName = formatIndexName(indexName);
        try {
            Response response = client.getLowLevelClient()
                                      .performRequest(new Request(HttpGet.METHOD_NAME, "/" + indexName));
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                healthChecker.health();
                throw new IOException(
                    "The response status code of template exists request should be 200, but it is " + statusCode);
            }
            Type type = new TypeToken<HashMap<String, Object>>() {
            }.getType();
            Map<String, Object> templates = new Gson().<HashMap<String, Object>>fromJson(
                new InputStreamReader(response.getEntity().getContent()),
                type
            );
            return (Map<String, Object>) Optional.ofNullable(templates.get(indexName)).orElse(new HashMap<>());
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return new HashMap<>();
            }
            healthChecker.unHealth(e);
            throw e;
        } catch (IOException t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    @Override
    public boolean updateIndexMapping(String indexName, final Map<String, Object> mapping) throws IOException {
        indexName = formatIndexName(indexName);
        PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
        Gson gson = new Gson();
        putMappingRequest.source(gson.toJson(mapping), XContentType.JSON);
        putMappingRequest.type("_doc");
        AcknowledgedResponse response = client.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
        log.debug("put {} index mapping finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    @Override
    public Map<String, Object> getTemplate(String name) throws IOException {
        name = formatIndexName(name);
        try {
            Response response = client.getLowLevelClient()
                                      .performRequest(new Request(HttpGet.METHOD_NAME, "/_template/" + name));
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                healthChecker.health();
                throw new IOException(
                    "The response status code of template exists request should be 200, but it is " + statusCode);
            }
            Type type = new TypeToken<HashMap<String, Object>>() {
            }.getType();
            Map<String, Object> templates = new Gson().<HashMap<String, Object>>fromJson(
                new InputStreamReader(response.getEntity().getContent()),
                type
            );
            if (templates.containsKey(name)) {
                return (Map<String, Object>) templates.get(name);
            }
            return new HashMap<>();
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return new HashMap<>();
            }
            healthChecker.unHealth(e);
            throw e;
        } catch (IOException t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    @Override
    public boolean isExistsIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);
        GetIndexRequest request = new GetIndexRequest(indexName);
        return client.indices().exists(request, RequestOptions.DEFAULT);
    }

    @Override
    public boolean isExistsTemplate(String indexName) throws IOException {
        indexName = formatIndexName(indexName);

        IndexTemplatesExistRequest indexTemplatesExistRequest = new IndexTemplatesExistRequest(indexName);

        return client.indices().existsTemplate(indexTemplatesExistRequest, RequestOptions.DEFAULT);
    }

    @Override
    public boolean createOrUpdateTemplate(String indexName, Map<String, Object> settings,
                                          Map<String, Object> mapping) throws IOException {
        indexName = formatIndexName(indexName);

        PutIndexTemplateRequest putIndexTemplateRequest = new PutIndexTemplateRequest(indexName).patterns(
            Collections.singletonList(indexName + "-*"))
                                                                                                .alias(new Alias(
                                                                                                    indexName))
                                                                                                .settings(settings)
                                                                                                .mapping(mapping);

        AcknowledgedResponse acknowledgedResponse = client.indices()
                                                          .putTemplate(putIndexTemplateRequest, RequestOptions.DEFAULT);

        return acknowledgedResponse.isAcknowledged();
    }

    @Override
    public boolean deleteTemplate(String indexName) throws IOException {
        indexName = formatIndexName(indexName);

        DeleteIndexTemplateRequest deleteIndexTemplateRequest = new DeleteIndexTemplateRequest(indexName);
        AcknowledgedResponse acknowledgedResponse = client.indices()
                                                          .deleteTemplate(
                                                              deleteIndexTemplateRequest, RequestOptions.DEFAULT);

        return acknowledgedResponse.isAcknowledged();
    }

    @Override
    public SearchResponse doSearch(SearchSourceBuilder searchSourceBuilder, String... indexNames) throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexNames);
        searchRequest.indicesOptions(IndicesOptions.fromOptions(true, true, true, false));
        searchRequest.source(searchSourceBuilder);
        try {
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
            healthChecker.health();
            return response;
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            handleIOPoolStopped(t);
            throw t;
        }
    }

    @Override
    public GetResponse get(String indexName, String id) throws IOException {
        indexName = formatIndexName(indexName);
        GetRequest request = new GetRequest(indexName, id);
        try {
            GetResponse response = client.get(request, RequestOptions.DEFAULT);
            healthChecker.health();
            return response;
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    @Override
    public SearchResponse ids(String indexName, String[] ids) throws IOException {
        indexName = formatIndexName(indexName);

        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source().query(QueryBuilders.idsQuery().addIds(ids)).size(ids.length);
        try {
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
            healthChecker.health();
            return response;
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    @Override
    public void forceInsert(String indexName, String id, XContentBuilder source) throws IOException {
        IndexRequest request = (IndexRequest) prepareInsert(indexName, id, source);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        try {
            client.index(request, RequestOptions.DEFAULT);
            healthChecker.health();
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    @Override
    public void forceUpdate(String indexName, String id, XContentBuilder source) throws IOException {
        org.elasticsearch.action.update.UpdateRequest request = (org.elasticsearch.action.update.UpdateRequest) prepareUpdate(
            indexName, id, source);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        try {
            client.update(request, RequestOptions.DEFAULT);
            healthChecker.health();
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    @Override
    public InsertRequest prepareInsert(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new ElasticSearch7InsertRequest(indexName, id).source(source);
    }

    @Override
    public UpdateRequest prepareUpdate(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new ElasticSearch7UpdateRequest(indexName, id).doc(source);
    }

    @Override
    public int delete(String indexName, String timeBucketColumnName, long endTimeBucket) throws IOException {
        indexName = formatIndexName(indexName);

        DeleteByQueryRequest deleteByQueryRequest = new DeleteByQueryRequest(indexName);
        deleteByQueryRequest.setAbortOnVersionConflict(false);
        deleteByQueryRequest.setQuery(QueryBuilders.rangeQuery(timeBucketColumnName).lte(endTimeBucket));
        BulkByScrollResponse bulkByScrollResponse = client.deleteByQuery(deleteByQueryRequest, RequestOptions.DEFAULT);
        log.debug(
            "delete indexName: {}, by query request: {}, response: {}", indexName, deleteByQueryRequest,
            bulkByScrollResponse
        );
        return HttpStatus.SC_OK;
    }

    /**
     * @since 8.7.0 SkyWalking don't use sync bulk anymore. This method is just kept for unexpected case in the future.
     */
    @Deprecated
    @Override
    public void synchronousBulk(BulkRequest request) {
        request.timeout(TimeValue.timeValueMinutes(2));
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
        request.waitForActiveShards(ActiveShardCount.ONE);
        try {
            int size = request.requests().size();
            BulkResponse responses = client.bulk(request, RequestOptions.DEFAULT);
            log.info("Synchronous bulk took time: {} millis, size: {}", responses.getTook().getMillis(), size);
            healthChecker.health();
        } catch (Throwable t) {
            healthChecker.unHealth(t);
        }
    }

    @Override
    public BulkProcessor createBulkProcessor(int bulkActions, int flushInterval, int concurrentRequests) {
        BulkProcessor.Listener listener = createBulkListener();

        return BulkProcessor.builder(
            (bulkRequest, bulkResponseActionListener) -> client.bulkAsync(bulkRequest, RequestOptions.DEFAULT,
                                                                          bulkResponseActionListener
            ), listener)
                            .setBulkActions(bulkActions)
                            .setFlushInterval(TimeValue.timeValueSeconds(flushInterval))
                            .setConcurrentRequests(concurrentRequests)
                            .setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 3))
                            .build();
    }
}
