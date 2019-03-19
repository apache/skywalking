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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.skywalking.oap.server.library.client.Client;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.bulk.BackoffPolicy;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author peng-yongsheng
 */
public class ElasticSearchClient implements Client {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchClient.class);

    private static final String TYPE = "type";
    private final String clusterNodes;
    private final String namespace;
    private RestHighLevelClient client;
    @Getter private final List<String> createByDayIndexes;

    public ElasticSearchClient(String clusterNodes, String namespace, List<String> createByDayIndexes) {
        this.clusterNodes = clusterNodes;
        this.namespace = namespace;
        this.createByDayIndexes = createByDayIndexes;
    }

    @Override public void connect() {
        List<HttpHost> pairsList = parseClusterNodes(clusterNodes);

        client = new RestHighLevelClient(
            RestClient.builder(pairsList.toArray(new HttpHost[0])));
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
        if (!this.isDailyIndex(indexName)) {
            return createIndexSingle(indexName, settings, mappingBuilder);
        } else {
            DateTime currentTime = new DateTime();
            boolean result = true;
            for (int i = 0; i < 3; i++) {
                String newIndexName = indexName + "_" + currentTime.plusDays(i).toString("yyyyMMdd");
                if (result && !this.isExistsIndex(newIndexName)) {
                    result = createIndexSingle(newIndexName, settings, mappingBuilder);
                }
            }
            return result;
        }
    }

    private boolean createIndexSingle(String indexName, Settings settings,
        XContentBuilder mappingBuilder) throws IOException {
        indexName = formatIndexName(indexName);
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.settings(settings);
        request.mapping(TYPE, mappingBuilder);
        CreateIndexResponse response = client.indices().create(request);
        logger.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    public boolean deleteIndex(String indexName) throws IOException {
        indexName = formatIndexName(indexName);
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

    public SearchResponse search(String indexName, SearchSourceBuilder searchSourceBuilder) throws IOException {
        return this.search(searchSourceBuilder, this.getAllIndexName(indexName));
    }

    public SearchResponse search(String indexName, SearchSourceBuilder searchSourceBuilder, long startSecondTB, long endSecondTB) throws IOException {
        if (startSecondTB > 0 && endSecondTB > 0) {
            return this.search(searchSourceBuilder, this.getIndexNameByDate(indexName, startSecondTB, endSecondTB));
        } else {
            return this.search(searchSourceBuilder, this.getAllIndexName(indexName));
        }
    }

    public boolean isDailyIndex(String indexName) {
        return this.createByDayIndexes.contains(indexName);
    }

    private SearchResponse search(SearchSourceBuilder searchSourceBuilder, String[] indexName) throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.indicesOptions(IndicesOptions.fromOptions(true, true, true, false));
        searchRequest.types(TYPE);
        searchRequest.source(searchSourceBuilder);
        return client.search(searchRequest);
    }

    private String[] getIndexNameByDate(String indexName, Long startSecondTB, Long endSecondTB) {
        String[] indexes;
        if (this.isDailyIndex(indexName)) {
            DateTimeFormatter format = DateTimeFormat.forPattern("yyyyMMdd");
            DateTime starts = DateTime.parse(startSecondTB.toString().substring(0, 8), format);
            DateTime ends = DateTime.parse(endSecondTB.toString().substring(0, 8), format);
            int length = Days.daysBetween(starts, ends).getDays();
            if (length < 1) {
                length = 0;
            }
            String[] rs = new String[length + 1];
            for (int j = 0; j < rs.length; j++) {
                rs[j] = formatIndexName(indexName) + "_" + starts.plusDays(j).toString("yyyyMMdd");
            }
            return rs;
        } else {
            indexes = new String[]{formatIndexName(indexName)};
        }
        return indexes;
    }

    private String[] getAllIndexName(final String indexName) {
        String[] indexes;
        if (this.isDailyIndex(indexName)) {
            List<String> in = new ArrayList<>();
            final String formatIndexName = formatIndexName(indexName);
            try {
                Response response = client.getLowLevelClient().performRequest("GET","/" + formatIndexName + "_*");
                HttpEntity entity = response.getEntity();
                JsonObject content = (JsonObject) new JsonParser().parse(EntityUtils.toString(entity));
                content.entrySet().forEach(entry -> {
                    String date = StringUtils.substringAfter(entry.getKey(), formatIndexName + "_");
                    try {
                        if (Long.parseLong(date) < 29999931 && 20000000 < Long.parseLong(date)) {
                            in.add(entry.getKey());
                        }
                    } catch (Exception e) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("{} is not {} format index name", entry.getKey(), indexName);
                        }
                    }
                });

                indexes = new String[in.size()];
                indexes = in.toArray(indexes);
                return indexes;
            } catch (Exception e) {
                logger.error("get all index error:{}", e);
            }
        } else {
            return new String[]{formatIndexName(indexName)};
        }
        return null;
    }

    public GetResponse get(String indexName, String id) throws IOException {
        indexName = formatIndexName(indexName);
        GetRequest request = new GetRequest(indexName, TYPE, id);
        return client.get(request);
    }

    public GetResponse get(String indexName, String id, Long timeBucket) throws IOException {
        GetRequest request = new GetRequest(getIndexNameByDate(indexName, timeBucket, timeBucket)[0], TYPE, id);
        return client.get(request);
    }

    public MultiGetResponse multiGet(String indexName, List<String> ids) throws IOException {
        final String newIndexName = formatIndexName(indexName);
        MultiGetRequest request = new MultiGetRequest();
        ids.forEach(id -> request.add(newIndexName, TYPE, id));
        return client.multiGet(request);
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

    public IndexRequest prepareInsert(String indexName, String id, XContentBuilder source, Long timeBucket) {
        return new IndexRequest(getIndexNameByDate(indexName, timeBucket, timeBucket)[0], TYPE, id).source(source);
    }

    public UpdateRequest prepareUpdate(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new UpdateRequest(indexName, TYPE, id).doc(source);
    }

    public UpdateRequest prepareUpdate(String indexName, String id, XContentBuilder source, Long timeBucket) {
        return new UpdateRequest(getIndexNameByDate(indexName, timeBucket, timeBucket)[0], TYPE, id).doc(source);
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
        Response response = client.getLowLevelClient().performRequest("POST", "/" + indexName + "/_delete_by_query", params, entity);
        logger.debug("delete indexName: {}, jsonString : {}", indexName, jsonString);
        return response.getStatusLine().getStatusCode();
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

            }

            @Override
            public void afterBulk(long executionId, BulkRequest request,
                BulkResponse response) {

            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                logger.error("{} data bulk failed, reason: {}", request.numberOfActions(), failure);
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
