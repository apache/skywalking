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

import com.google.gson.*;
import java.io.*;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.skywalking.oap.server.library.client.Client;
import org.elasticsearch.action.admin.indices.create.*;
import org.elasticsearch.action.admin.indices.delete.*;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.bulk.*;
import org.elasticsearch.action.get.*;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.*;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.*;
import org.elasticsearch.common.unit.*;
import org.elasticsearch.common.xcontent.*;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ElasticSearchClient implements Client {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchClient.class);

    public static final String TYPE = "type";
    private final String clusterNodes;
    private final String namespace;
    private final String namespacePrefix;
    private final String user;
    private final String password;
    private RestHighLevelClient client;

    public ElasticSearchClient(String clusterNodes, String namespace, String user, String password) {
        this.clusterNodes = clusterNodes;
        this.namespace = namespace;
        this.namespacePrefix = namespace + "_";
        this.user = user;
        this.password = password;
    }

    @Override public void connect() throws IOException {
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
        client.ping();
    }

    @Override public void shutdown() throws IOException {
        client.close();
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

    public boolean createIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);

        CreateIndexRequest request = new CreateIndexRequest(indexName);
        CreateIndexResponse response = client.indices().create(request);
        logger.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public boolean createIndex(String indexName, JsonObject settings, JsonObject mapping) throws IOException {
        indexName = formatIndexName(indexName);
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.settings(settings.toString(), XContentType.JSON);
        request.mapping(TYPE, mapping.toString(), XContentType.JSON);
        CreateIndexResponse response = client.indices().create(request);
        logger.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public List<String> retrievalIndexByAliases(String aliases) throws IOException {
        aliases = formatIndexName(aliases);
        Response response = client.getLowLevelClient().performRequest(HttpGet.METHOD_NAME, "/_alias/" + aliases);
        if (HttpStatus.SC_OK == response.getStatusLine().getStatusCode()) {
            Gson gson = new Gson();
            InputStreamReader reader = new InputStreamReader(response.getEntity().getContent());
            JsonObject responseJson = gson.fromJson(reader, JsonObject.class);
            logger.debug("retrieval indexes by aliases {}, response is {}", aliases, responseJson);
            return new ArrayList<>(responseJson.keySet());
        }
        return Collections.EMPTY_LIST;
    }

    public JsonObject getIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);
        GetIndexRequest request = new GetIndexRequest();
        request.indices(indexName);
        Response response = client.getLowLevelClient().performRequest(HttpGet.METHOD_NAME, "/" + indexName);
        InputStreamReader reader = new InputStreamReader(response.getEntity().getContent());
        Gson gson = new Gson();
        return gson.fromJson(reader, JsonObject.class);
    }

    /**
     *  If your indexName is retrieved from elasticsearch through {@link #retrievalIndexByAliases(String)} or some other method and it already contains namespace.
     *  Then you should delete the index by this method, this method will no longer concatenate namespace.
     *
     * https://github.com/apache/skywalking/pull/3017
     *
     */
    public boolean deleteByIndexName(String indexName) throws IOException {
        return deleteIndex(indexName, false);
    }

    /**
     *  If your indexName is obtained from metadata or configuration and without namespace.
     *  Then you should delete the index by this method, this method automatically concatenates namespace.
     *
     *  https://github.com/apache/skywalking/pull/3017
     *
     */
    public boolean deleteByModelName(String modelName) throws IOException {
        return deleteIndex(modelName, true);
    }

    private boolean deleteIndex(String indexName, boolean formatIndexName) throws IOException {
        if (formatIndexName) {
            indexName = formatIndexName(indexName);
        }
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        DeleteIndexResponse response;
        response = client.indices().delete(request);
        logger.debug("delete {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public boolean isExistsIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);
        GetIndexRequest request = new GetIndexRequest();
        request.indices(indexName);
        return client.indices().exists(request);
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
            throw new IOException("The response status code of template exists request should be 200 or 404, but it is " + statusCode);
        }
    }

    public boolean createTemplate(String indexName, JsonObject settings, JsonObject mapping) throws IOException {
        indexName = formatIndexName(indexName);

        JsonArray patterns = new JsonArray();
        patterns.add(indexName + "-*");

        JsonObject aliases = new JsonObject();
        aliases.add(indexName, new JsonObject());

        JsonObject template = new JsonObject();
        template.add("index_patterns", patterns);
        template.add("aliases", aliases);
        template.add("settings", settings);
        template.add("mappings", mapping);

        HttpEntity entity = new NStringEntity(template.toString(), ContentType.APPLICATION_JSON);

        Response response = client.getLowLevelClient().performRequest(HttpPut.METHOD_NAME, "/_template/" + indexName, Collections.emptyMap(), entity);
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
    }

    public boolean deleteTemplate(String indexName) throws IOException {
        indexName = formatIndexName(indexName);

        Response response = client.getLowLevelClient().performRequest(HttpDelete.METHOD_NAME, "/_template/" + indexName);
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
    }

    public SearchResponse search(String indexName, SearchSourceBuilder searchSourceBuilder) throws IOException {
        indexName = formatIndexName(indexName);
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.types(TYPE);
        searchRequest.source(searchSourceBuilder);
        return client.search(searchRequest);
    }

    public GetResponse get(String indexName, String id) throws IOException {
        indexName = formatIndexName(indexName);
        GetRequest request = new GetRequest(indexName, TYPE, id);
        return client.get(request);
    }

    public SearchResponse idQuery(String indexName, String id) throws IOException {
        indexName = formatIndexName(indexName);

        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.types(TYPE);
        searchRequest.source().query(QueryBuilders.idsQuery().addIds(id));
        return client.search(searchRequest);
    }

    public Map<String, Map<String, Object>> ids(String indexName, String... ids) throws IOException {
        indexName = formatIndexName(indexName);

        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.types(TYPE);
        searchRequest.source().query(QueryBuilders.idsQuery().addIds(ids)).size(ids.length);
        SearchResponse response = client.search(searchRequest);

        Map<String, Map<String, Object>> result = new HashMap<>();
        SearchHit[] hits = response.getHits().getHits();
        for (SearchHit hit : hits) {
            result.put(hit.getId(), hit.getSourceAsMap());
        }
        return result;
    }

    public void forceInsert(String indexName, String id, XContentBuilder source) throws IOException {
        IndexRequest request = prepareInsert(indexName, id, source);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.index(request);
    }

    public void forceUpdate(String indexName, String id, XContentBuilder source, long version) throws IOException {
        UpdateRequest request = prepareUpdate(indexName, id, source);
        request.version(version);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.update(request);
    }

    public void forceUpdate(String indexName, String id, XContentBuilder source) throws IOException {
        UpdateRequest request = prepareUpdate(indexName, id, source);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.update(request);
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
        Map<String, String> params = Collections.singletonMap("conflicts", "proceed");
        String jsonString = "{" +
            "  \"query\": {" +
            "    \"range\": {" +
            "      \"" + timeBucketColumnName + "\": {" +
            "        \"lte\": " + endTimeBucket +
            "      }" +
            "    }" +
            "  }" +
            "}";
        HttpEntity entity = new NStringEntity(jsonString, ContentType.APPLICATION_JSON);
        Response response = client.getLowLevelClient().performRequest(HttpPost.METHOD_NAME, "/" + indexName + "/_delete_by_query", params, entity);
        logger.debug("delete indexName: {}, jsonString : {}", indexName, jsonString);
        return response.getStatusLine().getStatusCode();
    }

    public String formatIndexName(String indexName) {
        if (StringUtils.isNotEmpty(namespace)) {
            return namespacePrefix + indexName;
        }
        return indexName;
    }

    public BulkProcessor createBulkProcessor(int bulkActions, int bulkSize, int flushInterval, int concurrentRequests) {
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
                    logger.info("Bulk [{}] completed in {} milliseconds", executionId, response.getTook().getMillis());
                }
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                logger.error("Failed to execute bulk", failure);
            }
        };

        return BulkProcessor.builder(client::bulkAsync, listener)
            .setBulkActions(bulkActions)
            .setBulkSize(new ByteSizeValue(bulkSize, ByteSizeUnit.MB))
            .setFlushInterval(TimeValue.timeValueSeconds(flushInterval))
            .setConcurrentRequests(concurrentRequests)
            .setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 3))
            .build();
    }
}
