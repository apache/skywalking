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

import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLContext;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.library.util.HealthChecker;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
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
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * ElasticSearchClient connects to the ES server by using ES client APIs.
 */
@Slf4j
@RequiredArgsConstructor
public class ElasticSearchClient implements Client, HealthCheckable {
    public static final String TYPE = "type";
    protected final String clusterNodes;
    protected final String protocol;
    private final String trustStorePath;
    @Setter
    private volatile String trustStorePass;
    @Setter
    private volatile String user;
    @Setter
    private volatile String password;
    private final List<IndexNameConverter> indexNameConverters;
    protected volatile RestHighLevelClient client;
    protected DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();
    protected final ReentrantLock connectLock = new ReentrantLock();
    private final int connectTimeout;
    private final int socketTimeout;

    public ElasticSearchClient(String clusterNodes,
                               String protocol,
                               String trustStorePath,
                               String trustStorePass,
                               String user,
                               String password,
                               List<IndexNameConverter> indexNameConverters,
                               int connectTimeout,
                               int socketTimeout) {
        this.clusterNodes = clusterNodes;
        this.protocol = protocol;
        this.user = user;
        this.password = password;
        this.indexNameConverters = indexNameConverters;
        this.trustStorePath = trustStorePath;
        this.trustStorePass = trustStorePass;
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
    }

    @Override
    public void connect() throws IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, CertificateException {
        connectLock.lock();
        try {
            List<HttpHost> hosts = parseClusterNodes(protocol, clusterNodes);
            if (client != null) {
                try {
                    client.close();
                } catch (Throwable t) {
                    log.error("ElasticSearch client reconnection fails based on new config", t);
                }
            }
            client = createClient(hosts);
            client.ping();
        } finally {
            connectLock.unlock();
        }
    }

    protected RestHighLevelClient createClient(
        final List<HttpHost> pairsList) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, KeyManagementException {
        RestClientBuilder builder;
        if (StringUtil.isNotEmpty(user) && StringUtil.isNotEmpty(password)) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));

            if (StringUtil.isEmpty(trustStorePath)) {
                builder = RestClient.builder(pairsList.toArray(new HttpHost[0]))
                                    .setHttpClientConfigCallback(
                                        httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(
                                            credentialsProvider));
            } else {
                KeyStore truststore = KeyStore.getInstance("jks");
                try (InputStream is = Files.newInputStream(Paths.get(trustStorePath))) {
                    truststore.load(is, trustStorePass.toCharArray());
                }
                SSLContextBuilder sslBuilder = SSLContexts.custom().loadTrustMaterial(truststore, null);
                final SSLContext sslContext = sslBuilder.build();
                builder = RestClient.builder(pairsList.toArray(new HttpHost[0]))
                                    .setHttpClientConfigCallback(
                                        httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(
                                            credentialsProvider)
                                                                              .setSSLContext(sslContext));
            }
        } else {
            builder = RestClient.builder(pairsList.toArray(new HttpHost[0]));
        }
        builder.setRequestConfigCallback(
            requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(socketTimeout)
        );

        return new RestHighLevelClient(builder);
    }

    @Override
    public void shutdown() throws IOException {
        client.close();
    }

    public static List<HttpHost> parseClusterNodes(String protocol, String nodes) {
        List<HttpHost> httpHosts = new LinkedList<>();
        log.info("elasticsearch cluster nodes: {}", nodes);
        List<String> nodesSplit = Splitter.on(",").omitEmptyStrings().splitToList(nodes);

        for (String node : nodesSplit) {
            String host = node.split(":")[0];
            String port = node.split(":")[1];
            httpHosts.add(new HttpHost(host, Integer.parseInt(port), protocol));
        }

        return httpHosts;
    }

    public boolean createIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);

        CreateIndexRequest request = new CreateIndexRequest(indexName);
        CreateIndexResponse response = client.indices().create(request);
        log.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public boolean updateIndexMapping(String indexName, Map<String, Object> mapping) throws IOException {
        indexName = formatIndexName(indexName);
        Map<String, Object> properties = (Map<String, Object>) mapping.get(ElasticSearchClient.TYPE);
        PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
        Gson gson = new Gson();
        putMappingRequest.type(ElasticSearchClient.TYPE);
        putMappingRequest.source(gson.toJson(properties), XContentType.JSON);
        PutMappingResponse response = client.indices().putMapping(putMappingRequest);
        log.debug("put {} index mapping finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public Map<String, Object> getIndex(String indexName) throws IOException {
        if (StringUtil.isBlank(indexName)) {
            return new HashMap<>();
        }
        indexName = formatIndexName(indexName);
        try {
            Response response = client.getLowLevelClient()
                                      .performRequest(HttpGet.METHOD_NAME, "/" + indexName);
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

    public boolean createIndex(String indexName, Map<String, Object> settings,
                               Map<String, Object> mapping) throws IOException {
        indexName = formatIndexName(indexName);
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        Gson gson = new Gson();
        request.settings(gson.toJson(settings), XContentType.JSON);
        request.mapping(TYPE, gson.toJson(mapping), XContentType.JSON);
        CreateIndexResponse response = client.indices().create(request);
        log.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public List<String> retrievalIndexByAliases(String aliases) throws IOException {
        aliases = formatIndexName(aliases);
        Response response;
        try {
            response = client.getLowLevelClient().performRequest(HttpGet.METHOD_NAME, "/_alias/" + aliases);
            healthChecker.health();
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
        if (HttpStatus.SC_OK == response.getStatusLine().getStatusCode()) {
            Gson gson = new Gson();
            InputStreamReader reader;
            try {
                reader = new InputStreamReader(response.getEntity().getContent());
            } catch (Throwable t) {
                healthChecker.unHealth(t);
                throw t;
            }
            JsonObject responseJson = gson.fromJson(reader, JsonObject.class);
            log.debug("retrieval indexes by aliases {}, response is {}", aliases, responseJson);
            return new ArrayList<>(responseJson.keySet());
        }
        return Collections.emptyList();
    }

    /**
     * If your indexName is retrieved from elasticsearch through {@link #retrievalIndexByAliases(String)} or some other
     * method and it already contains namespace. Then you should delete the index by this method, this method will no
     * longer concatenate namespace.
     * <p>
     * https://github.com/apache/skywalking/pull/3017
     */
    public boolean deleteByIndexName(String indexName) throws IOException {
        return deleteIndex(indexName, false);
    }

    /**
     * If your indexName is obtained from metadata or configuration and without namespace. Then you should delete the
     * index by this method, this method automatically concatenates namespace.
     * <p>
     * https://github.com/apache/skywalking/pull/3017
     */
    public boolean deleteByModelName(String modelName) throws IOException {
        return deleteIndex(modelName, true);
    }

    protected boolean deleteIndex(String indexName, boolean formatIndexName) throws IOException {
        if (formatIndexName) {
            indexName = formatIndexName(indexName);
        }
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        DeleteIndexResponse response;
        response = client.indices().delete(request);
        log.debug("delete {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public boolean isExistsIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);
        GetIndexRequest request = new GetIndexRequest();
        request.indices(indexName);
        return client.indices().exists(request);
    }

    public Map<String, Object> getTemplate(String name) throws IOException {
        name = formatIndexName(name);
        try {
            Response response = client.getLowLevelClient()
                                      .performRequest(HttpGet.METHOD_NAME, "/_template/" + name);
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

    public boolean isExistsTemplate(String indexName) throws IOException {
        indexName = formatIndexName(indexName);

        Response response = client.getLowLevelClient().performRequest(HttpHead.METHOD_NAME, "/_template/" + indexName);

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_OK) {
            return true;
        } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
            return false;
        } else {
            throw new IOException(
                "The response status code of template exists request should be 200 or 404, but it is " + statusCode);
        }
    }

    public boolean createOrUpdateTemplate(String indexName, Map<String, Object> settings,
                                          Map<String, Object> mapping) throws IOException {
        indexName = formatIndexName(indexName);

        String[] patterns = new String[] {indexName + "-*"};

        Map<String, Object> aliases = new HashMap<>();
        aliases.put(indexName, new JsonObject());

        Map<String, Object> template = new HashMap<>();
        template.put("index_patterns", patterns);
        template.put("aliases", aliases);
        template.put("settings", settings);
        template.put("mappings", mapping);

        HttpEntity entity = new NStringEntity(new Gson().toJson(template), ContentType.APPLICATION_JSON);

        Response response = client.getLowLevelClient()
                                  .performRequest(
                                      HttpPut.METHOD_NAME, "/_template/" + indexName, Collections.emptyMap(), entity);
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
    }

    public boolean deleteTemplate(String indexName) throws IOException {
        indexName = formatIndexName(indexName);
        Response response = client.getLowLevelClient()
                                  .performRequest(HttpDelete.METHOD_NAME, "/_template/" + indexName);
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
    }

    public SearchResponse search(IndexNameMaker indexNameMaker,
                                 SearchSourceBuilder searchSourceBuilder) throws IOException {
        String[] indexNames = Arrays.stream(indexNameMaker.make()).map(this::formatIndexName).toArray(String[]::new);
        return doSearch(searchSourceBuilder, indexNames);
    }

    public SearchResponse search(String indexName, SearchSourceBuilder searchSourceBuilder) throws IOException {
        indexName = formatIndexName(indexName);
        return doSearch(searchSourceBuilder, indexName);
    }

    protected SearchResponse doSearch(SearchSourceBuilder searchSourceBuilder,
                                      String... indexNames) throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexNames);
        searchRequest.indicesOptions(IndicesOptions.fromOptions(true, true, true, false));
        searchRequest.types(TYPE);
        searchRequest.source(searchSourceBuilder);
        try {
            SearchResponse response = client.search(searchRequest);
            healthChecker.health();
            return response;
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            handleIOPoolStopped(t);
            throw t;
        }
    }

    protected void handleIOPoolStopped(Throwable t) throws IOException {
        if (!(t instanceof IllegalStateException)) {
            return;
        }
        IllegalStateException ise = (IllegalStateException) t;
        // Fixed the issue described in https://github.com/elastic/elasticsearch/issues/39946
        if (ise.getMessage().contains("I/O reactor status: STOPPED") &&
            connectLock.tryLock()) {
            try {
                connect();
            } catch (KeyStoreException | NoSuchAlgorithmException | KeyManagementException | CertificateException e) {
                throw new IllegalStateException("Can't reconnect to Elasticsearch", e);
            }
        }
    }

    public GetResponse get(String indexName, String id) throws IOException {
        indexName = formatIndexName(indexName);
        GetRequest request = new GetRequest(indexName, TYPE, id);
        try {
            GetResponse response = client.get(request);
            healthChecker.health();
            return response;
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    public SearchResponse ids(String indexName, String[] ids) throws IOException {
        indexName = formatIndexName(indexName);

        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.types(TYPE);
        searchRequest.source().query(QueryBuilders.idsQuery().addIds(ids)).size(ids.length);
        try {
            SearchResponse response = client.search(searchRequest);
            healthChecker.health();
            return response;
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    public void forceInsert(String indexName, String id, XContentBuilder source) throws IOException {
        IndexRequest request = (IndexRequest) prepareInsert(indexName, id, source);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        try {
            client.index(request);
            healthChecker.health();
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    public void forceUpdate(String indexName, String id, XContentBuilder source) throws IOException {
        org.elasticsearch.action.update.UpdateRequest request = (org.elasticsearch.action.update.UpdateRequest) prepareUpdate(
            indexName, id, source);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        try {
            client.update(request);
            healthChecker.health();
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    public InsertRequest prepareInsert(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new ElasticSearchInsertRequest(indexName, TYPE, id).source(source);
    }

    public UpdateRequest prepareUpdate(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new ElasticSearchUpdateRequest(indexName, TYPE, id).doc(source);
    }

    public int delete(String indexName, String timeBucketColumnName, long endTimeBucket) throws IOException {
        indexName = formatIndexName(indexName);
        Map<String, String> params = Collections.singletonMap("conflicts", "proceed");
        String jsonString = "{" + "  \"query\": {" + "    \"range\": {" + "      \"" + timeBucketColumnName + "\": {" + "        \"lte\": " + endTimeBucket + "      }" + "    }" + "  }" + "}";
        HttpEntity entity = new NStringEntity(jsonString, ContentType.APPLICATION_JSON);
        Response response = client.getLowLevelClient()
                                  .performRequest(
                                      HttpPost.METHOD_NAME, "/" + indexName + "/_delete_by_query", params, entity);
        log.debug("delete indexName: {}, jsonString : {}", indexName, jsonString);
        return response.getStatusLine().getStatusCode();
    }

    /**
     * @since 8.7.0 SkyWalking don't use sync bulk anymore. This method is just kept for unexpected case in the future.
     */
    @Deprecated
    public void synchronousBulk(BulkRequest request) {
        request.timeout(TimeValue.timeValueMinutes(2));
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
        request.waitForActiveShards(ActiveShardCount.ONE);
        try {
            int size = request.requests().size();
            BulkResponse responses = client.bulk(request);
            log.info("Synchronous bulk took time: {} millis, size: {}", responses.getTook().getMillis(), size);
            healthChecker.health();
        } catch (Throwable t) {
            healthChecker.unHealth(t);
        }
    }

    public BulkProcessor createBulkProcessor(int bulkActions, int flushInterval, int concurrentRequests) {
        BulkProcessor.Listener listener = createBulkListener();

        return BulkProcessor.builder(client::bulkAsync, listener)
                            .setBulkActions(bulkActions)
                            .setFlushInterval(TimeValue.timeValueSeconds(flushInterval))
                            .setConcurrentRequests(concurrentRequests)
                            .setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 3))
                            .build();
    }

    protected BulkProcessor.Listener createBulkListener() {
        return new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId, BulkRequest request) {
                int numberOfActions = request.numberOfActions();
                log.debug("Executing bulk [{}] with {} requests", executionId, numberOfActions);
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
                if (response.hasFailures()) {
                    log.warn("Bulk [{}] executed with failures:[{}]", executionId, response.buildFailureMessage());
                } else {
                    log.info(
                        "Bulk execution id [{}] completed in {} milliseconds, size: {}", executionId, response.getTook()
                                                                                                              .getMillis(),
                        request
                            .requests()
                            .size()
                    );
                }
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                log.error("Failed to execute bulk", failure);
            }
        };
    }

    public String formatIndexName(String indexName) {
        for (final IndexNameConverter indexNameConverter : indexNameConverters) {
            indexName = indexNameConverter.convert(indexName);
        }
        return indexName;
    }

    @Override
    public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }
}
