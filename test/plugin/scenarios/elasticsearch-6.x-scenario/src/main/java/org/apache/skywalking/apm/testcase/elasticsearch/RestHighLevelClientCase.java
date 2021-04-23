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

package org.apache.skywalking.apm.testcase.elasticsearch;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequest;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.script.mustache.SearchTemplateRequest;
import org.elasticsearch.script.mustache.SearchTemplateResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static java.util.Collections.singletonMap;

@Component
public class RestHighLevelClientCase {

    private static final Logger LOGGER = LogManager.getLogger(RestHighLevelClientCase.class);

    @Autowired
    private RestHighLevelClient client;

    public boolean healthcheck() throws Exception {
        ClusterHealthRequest request = new ClusterHealthRequest();
        request.timeout(TimeValue.timeValueSeconds(10));
        request.waitForStatus(ClusterHealthStatus.GREEN);

        ClusterHealthResponse response = client.cluster().health(request, RequestOptions.DEFAULT);
        if (response.isTimedOut()) {
            String message = "elastic search node start fail!";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
        return true;
    }

    public boolean elasticsearch() throws Exception {
        String indexName = UUID.randomUUID().toString();
        try {
            //create
            createIndex(client, indexName);
            // index
            index(client, indexName);

            client.indices().refresh(new RefreshRequest(indexName), RequestOptions.DEFAULT);

            //get
            get(client, indexName);
            // search
            search(client, indexName);
            // scroll
            scroll(client, indexName);
            // scrollAsync
            scrollAsync(client, indexName);
            // clearScroll
            clearScroll(client, indexName);
            // clearScrollAsync
            clearScrollAsync(client, indexName);
            // searchTemplate
            searchTemplate(client, indexName);
            // searchTemplateAsync
            searchTemplateAsync(client, indexName);
            // update
            update(client, indexName);
            // analyze
            analyze(client, indexName);
            // analyzeAsync
            analyzeAsync(client, indexName);
            // deleteByQuery
            deleteByQuery(client, indexName);
            // deleteByQueryAsync
            deleteByQueryAsync(client, indexName);
            // delete
            delete(client, indexName);
        } finally {
            if (null != client) {
                client.close();
            }
        }
        return true;
    }

    private void createIndex(RestHighLevelClient client, String indexName) throws IOException {
        CreateIndexRequest request = new CreateIndexRequest(indexName);

        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        {
            builder.startObject("properties");
            {
                builder.startObject("author");
                {
                    builder.field("type", "keyword");
                }
                builder.endObject();
                builder.startObject("title");
                {
                    builder.field("type", "keyword");
                }
                builder.endObject();
            }
            builder.endObject();
        }
        builder.endObject();
        request.mapping(builder);

        request.settings(Settings.builder().put("index.number_of_shards", 1).put("index.number_of_replicas", 0));

        CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
        if (!createIndexResponse.isAcknowledged()) {
            String message = "elasticsearch create index fail.";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
    }

    private void index(RestHighLevelClient client, String indexName) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        {
            builder.field("author", "Marker");
            builder.field("title", "Java programing.");
        }
        builder.endObject();
        IndexRequest indexRequest = new IndexRequest(indexName, "_doc", "1").source(builder);

        IndexResponse indexResponse = client.index(indexRequest, RequestOptions.DEFAULT);
        if (indexResponse.status().getStatus() >= 400) {
            String message = "elasticsearch index data fail.";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
    }

    private void get(RestHighLevelClient client, String indexName) throws IOException {
        GetRequest getRequest = new GetRequest(indexName, "_doc", "1");
        GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);

        if (!getResponse.isExists()) {
            String message = "elasticsearch get data fail.";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
    }

    private void update(RestHighLevelClient client, String indexName) throws IOException {
        UpdateRequest request = new UpdateRequest(indexName, "_doc", "1");
        Map<String, Object> parameters = singletonMap("title", "c++ programing.");
        Script inline = new Script(ScriptType.INLINE, "painless", "ctx._source.title = params.title", parameters);
        request.script(inline);

        UpdateResponse updateResponse = client.update(request, RequestOptions.DEFAULT);
        if (updateResponse.getVersion() != 2) {
            String message = "elasticsearch update data fail.";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
    }

    private void analyze(RestHighLevelClient client, String indexName) throws IOException {
        AnalyzeRequest analyzeRequest = new AnalyzeRequest(indexName).text("SkyWalking");
        AnalyzeResponse analyzeResponse = client.indices().analyze(analyzeRequest, RequestOptions.DEFAULT);
        if (null == analyzeResponse.getTokens() || analyzeResponse.getTokens().size() < 1) {
            String message = "elasticsearch analyze index fail.";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
    }

    private void analyzeAsync(RestHighLevelClient client, String indexName) throws IOException {
        ActionListener<AnalyzeResponse> listener = new ActionListener<AnalyzeResponse>() {
            @Override
            public void onResponse(final AnalyzeResponse analyzeResponse) {
                if (null == analyzeResponse.getTokens() || analyzeResponse.getTokens().size() < 1) {
                    String message = "elasticsearch analyze index fail.";
                    LOGGER.error(message);
                    throw new RuntimeException(message);
                }
            }

            @Override
            public void onFailure(final Exception e) {
                LOGGER.error(e.getMessage());
                throw new RuntimeException(e);
            }
        };
        AnalyzeRequest analyzeRequest = new AnalyzeRequest(indexName).text("SkyWalking");
        client.indices().analyzeAsync(analyzeRequest, RequestOptions.DEFAULT, listener);
    }

    private void delete(RestHighLevelClient client, String indexName) throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        AcknowledgedResponse deleteIndexResponse = client.indices().delete(request, RequestOptions.DEFAULT);
        if (!deleteIndexResponse.isAcknowledged()) {
            String message = "elasticsearch delete index fail.";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
    }

    private void search(RestHighLevelClient client, String indexName) throws IOException {

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("author", "Marker"));
        sourceBuilder.from(0);
        sourceBuilder.size(10);

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(indexName);
        searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        if (!(searchResponse.getHits().totalHits > 0)) {
            String message = "elasticsearch search data fail.";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
    }

    private void scroll(RestHighLevelClient client, String indexName) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchAllQuery());

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(indexName);
        searchRequest.source(sourceBuilder);
        searchRequest.scroll(TimeValue.timeValueSeconds(30));
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        SearchScrollRequest scrollRequest = new SearchScrollRequest(searchResponse.getScrollId());
        client.scroll(scrollRequest, RequestOptions.DEFAULT);
    }

    private void scrollAsync(RestHighLevelClient client, String indexName) throws IOException, InterruptedException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchAllQuery());

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(indexName);
        searchRequest.source(sourceBuilder);
        searchRequest.scroll(TimeValue.timeValueSeconds(30));
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        final CountDownLatch countDownLatch = new CountDownLatch(1);

        ActionListener<SearchResponse> listener = new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(final SearchResponse searchResponse) {
                countDownLatch.countDown();
            }

            @Override
            public void onFailure(final Exception e) {
                countDownLatch.countDown();
                LOGGER.error(e.getMessage());
                throw new RuntimeException(e);
            }
        };

        SearchScrollRequest scrollRequest = new SearchScrollRequest(searchResponse.getScrollId());
        client.scrollAsync(scrollRequest, RequestOptions.DEFAULT, listener);

        countDownLatch.await();
    }

    private void clearScroll(RestHighLevelClient client, String indexName) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchAllQuery());

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(indexName);
        searchRequest.source(sourceBuilder);
        searchRequest.scroll(TimeValue.timeValueSeconds(30));
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(searchResponse.getScrollId());
        client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
    }

    private void clearScrollAsync(RestHighLevelClient client,
                                  String indexName) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchAllQuery());

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(indexName);
        searchRequest.source(sourceBuilder);
        searchRequest.scroll(TimeValue.timeValueSeconds(30));
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(searchResponse.getScrollId());
        client.clearScrollAsync(clearScrollRequest, RequestOptions.DEFAULT, new ActionListener<ClearScrollResponse>() {
            @Override
            public void onResponse(final ClearScrollResponse clearScrollResponse) {

            }

            @Override
            public void onFailure(final Exception e) {
                LOGGER.error(e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private void searchTemplate(RestHighLevelClient client, String indexName) throws IOException {

        SearchTemplateRequest searchTemplateRequest = new SearchTemplateRequest();
        searchTemplateRequest.setRequest(new SearchRequest(indexName));
        searchTemplateRequest.setScriptType(ScriptType.INLINE);

        searchTemplateRequest.setScript("{\n" +
                                            "  \"from\": \"{{from}}\",\n" +
                                            "  \"size\": \"{{size}}\",\n" +
                                            "  \"query\": {\n" +
                                            "    \"term\": {\n" +
                                            "      \"{{field}}\": {\n" +
                                            "        \"value\": \"{{value}}\"\n" +
                                            "      }\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}");

        Map<String, Object> scriptParams = new HashMap<>();
        scriptParams.put("field", "author");
        scriptParams.put("value", "Marker");
        scriptParams.put("size", 10);
        scriptParams.put("from", 0);
        searchTemplateRequest.setScriptParams(scriptParams);

        SearchTemplateResponse searchTemplateResponse = client.searchTemplate(
            searchTemplateRequest, RequestOptions.DEFAULT);
        if (!(searchTemplateResponse.getResponse().getHits().totalHits > 0)) {
            String message = "elasticsearch searchTemplateAsync data fail.";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
    }

    private void searchTemplateAsync(RestHighLevelClient client, String indexName) throws IOException {

        SearchTemplateRequest searchTemplateRequest = new SearchTemplateRequest();
        searchTemplateRequest.setRequest(new SearchRequest(indexName));
        searchTemplateRequest.setScriptType(ScriptType.INLINE);

        searchTemplateRequest.setScript("{\n" +
                                            "  \"from\": \"{{from}}\",\n" +
                                            "  \"size\": \"{{size}}\",\n" +
                                            "  \"query\": {\n" +
                                            "    \"term\": {\n" +
                                            "      \"{{field}}\": {\n" +
                                            "        \"value\": \"{{value}}\"\n" +
                                            "      }\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}");

        Map<String, Object> scriptParams = new HashMap<>();
        scriptParams.put("field", "author");
        scriptParams.put("value", "Marker");
        scriptParams.put("size", 10);
        scriptParams.put("from", 0);
        searchTemplateRequest.setScriptParams(scriptParams);

        client.searchTemplateAsync(
            searchTemplateRequest, RequestOptions.DEFAULT, new ActionListener<SearchTemplateResponse>() {
                @Override
                public void onResponse(final SearchTemplateResponse searchTemplateResponse) {
                    if (!(searchTemplateResponse.getResponse().getHits().totalHits > 0)) {
                        String message = "elasticsearch searchTemplateAsync data fail.";
                        LOGGER.error(message);
                        throw new RuntimeException(message);
                    }
                }

                @Override
                public void onFailure(final Exception e) {
                    LOGGER.error(e);
                    throw new RuntimeException();
                }
            });
    }

    private void deleteByQuery(RestHighLevelClient client, String indexName) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("author", "Marker1"));

        DeleteByQueryRequest deleteByQueryRequest = new DeleteByQueryRequest(indexName);
        deleteByQueryRequest.setQuery(sourceBuilder.query());
        BulkByScrollResponse bulkByScrollResponse = client.deleteByQuery(
            deleteByQueryRequest, RequestOptions.DEFAULT);
        bulkByScrollResponse.getStatus();
    }

    private void deleteByQueryAsync(RestHighLevelClient client,
                                    String indexName) throws IOException, InterruptedException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("author", "Marker2"));

        DeleteByQueryRequest deleteByQueryRequest = new DeleteByQueryRequest(indexName);
        deleteByQueryRequest.setQuery(sourceBuilder.query());

        client.deleteByQueryAsync(
            deleteByQueryRequest, RequestOptions.DEFAULT, new ActionListener<BulkByScrollResponse>() {
                @Override
                public void onResponse(final BulkByScrollResponse bulkByScrollResponse) {

                }

                @Override
                public void onFailure(final Exception e) {
                    LOGGER.error(e);
                    throw new RuntimeException();
                }
            }
        );
    }
}
