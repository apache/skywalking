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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ITElasticSearchClient {

    private static final Logger logger = LoggerFactory.getLogger(ITElasticSearchClient.class);

    private ElasticSearchClient client;

    private final String namespace;

    public ITElasticSearchClient() {
        namespace = "";
    }

    protected ITElasticSearchClient(String namespace) {
        this.namespace = namespace;
    }

    @Before
    public void before() throws Exception {
        final String esAddress = System.getProperty("elastic.search.address");
        final String esProtocol = System.getProperty("elastic.search.protocol");
        client = new ElasticSearchClient(esAddress, esProtocol, "", "", namespace, "test", "test");
        client.connect();
    }

    @After
    public void after() throws IOException {
        client.shutdown();
    }

    @Test
    public void indexOperate() throws IOException {
        Map<String, Object> settings = new HashMap<>();
        settings.put("number_of_shards", 2);
        settings.put("number_of_replicas", 2);

        Map<String, Object> doc = new HashMap<>();

        JsonObject properties = new JsonObject();
        doc.put("properties", properties);

        JsonObject column = new JsonObject();
        column.addProperty("type", "text");
        properties.add("column1", column);

        String indexName = "test_index_operate";
        client.createIndex(indexName, settings, doc);
        Assert.assertTrue(client.isExistsIndex(indexName));

        JsonObject index = getIndex(indexName);
        logger.info(index.toString());

        Assert.assertEquals(2, index.getAsJsonObject(indexName).getAsJsonObject("settings").getAsJsonObject("index").get("number_of_shards").getAsInt());
        Assert.assertEquals(2, index.getAsJsonObject(indexName).getAsJsonObject("settings").getAsJsonObject("index").get("number_of_replicas").getAsInt());

        Assert.assertEquals("text", index.getAsJsonObject(indexName).getAsJsonObject("mappings").getAsJsonObject("type").getAsJsonObject("properties").getAsJsonObject("column1").get("type").getAsString());

        Assert.assertTrue(client.deleteByModelName(indexName));
    }

    @Test
    public void documentOperate() throws IOException {
        String id = String.valueOf(System.currentTimeMillis());

        XContentBuilder builder = XContentFactory.jsonBuilder()
            .startObject()
            .field("user", "kimchy")
            .field("post_date", "2009-11-15T14:12:12")
            .field("message", "trying out Elasticsearch")
            .endObject();

        String indexName = "test_document_operate";
        client.forceInsert(indexName, id, builder);

        GetResponse response = client.get(indexName, id);
        Assert.assertEquals("kimchy", response.getSource().get("user"));
        Assert.assertEquals("trying out Elasticsearch", response.getSource().get("message"));

        builder = XContentFactory.jsonBuilder()
            .startObject()
            .field("user", "pengys")
            .endObject();
        client.forceUpdate(indexName, id, builder);

        response = client.get(indexName, id);
        Assert.assertEquals("pengys", response.getSource().get("user"));
        Assert.assertEquals("trying out Elasticsearch", response.getSource().get("message"));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("user", "pengys"));
        SearchResponse searchResponse = client.search(indexName, sourceBuilder);
        Assert.assertEquals("trying out Elasticsearch", searchResponse.getHits().getHits()[0].getSourceAsMap().get("message"));
    }

    @Test
    public void templateOperate() throws IOException {
        Map<String, Object> settings = new HashMap<>();
        settings.put("number_of_shards", 1);
        settings.put("number_of_replicas", 0);
        settings.put("index.refresh_interval", "3s");
        settings.put("analysis.analyzer.oap_analyzer.type", "stop");

        Map<String, Object> mapping = new HashMap<>();
        Map<String, Object> doc = new HashMap<>();
        mapping.put("type", doc);

        JsonObject properties = new JsonObject();
        doc.put("properties", properties);

        JsonObject column = new JsonObject();
        column.addProperty("type", "text");
        properties.add("name", column);

        String indexName = "template_operate";

        client.createTemplate(indexName, settings, mapping);

        Assert.assertTrue(client.isExistsTemplate(indexName));

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
            .field("name", "pengys")
            .endObject();
        client.forceInsert(indexName + "-2019", "testid", builder);

        JsonObject index = getIndex(indexName + "-2019");
        logger.info(index.toString());

        Assert.assertEquals(1, index.getAsJsonObject(indexName + "-2019").getAsJsonObject("settings").getAsJsonObject("index").get("number_of_shards").getAsInt());
        Assert.assertEquals(0, index.getAsJsonObject(indexName + "-2019").getAsJsonObject("settings").getAsJsonObject("index").get("number_of_replicas").getAsInt());

        client.deleteTemplate(indexName);
        Assert.assertFalse(client.isExistsTemplate(indexName));
    }

    @Test
    public void bulk() throws InterruptedException {
        BulkProcessor bulkProcessor = client.createBulkProcessor(2000, 10, 2);

        Map<String, String> source = new HashMap<>();
        source.put("column1", "value1");
        source.put("column2", "value2");

        for (int i = 0; i < 100; i++) {
            IndexRequest indexRequest = new IndexRequest("bulk_insert_test", "type", String.valueOf(i));
            indexRequest.source(source);
            bulkProcessor.add(indexRequest);
        }

        bulkProcessor.flush();
        bulkProcessor.awaitClose(2, TimeUnit.SECONDS);
    }

    @Test
    public void timeSeriesOperate() throws IOException {
        String indexName = "test_time_series_operate";
        String timeSeriesIndexName = indexName + "-2019";

        Map<String, Object> mapping = new HashMap<>();
        Map<String, Object> doc = new HashMap<>();
        mapping.put("type", doc);

        JsonObject properties = new JsonObject();
        doc.put("properties", properties);

        JsonObject column = new JsonObject();
        column.addProperty("type", "text");
        properties.add("name", column);

        client.createTemplate(indexName, new HashMap<>(), mapping);

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
            .field("name", "pengys")
            .endObject();
        client.forceInsert(timeSeriesIndexName, "testid", builder);

        List<String> indexes = client.retrievalIndexByAliases(indexName);
        Assert.assertEquals(1, indexes.size());
        String index = indexes.get(0);
        Assert.assertTrue(client.deleteByIndexName(index));
        Assert.assertFalse(client.isExistsIndex(timeSeriesIndexName));
    }

    private JsonObject getIndex(String indexName) throws IOException {
        indexName = client.formatIndexName(indexName);
        GetIndexRequest request = new GetIndexRequest();
        request.indices(indexName);

        Response response = getRestHighLevelClient().getLowLevelClient().performRequest(HttpGet.METHOD_NAME, "/" + indexName);
        InputStreamReader reader = new InputStreamReader(response.getEntity().getContent());
        Gson gson = new Gson();
        return undoFormatIndexName(gson.fromJson(reader, JsonObject.class));
    }

    private RestHighLevelClient getRestHighLevelClient() {
        return (RestHighLevelClient)Whitebox.getInternalState(client, "client");
    }

    private JsonObject undoFormatIndexName(JsonObject index) {
        if (StringUtils.isNotEmpty(namespace) && index != null && index.size() > 0) {
            logger.info("UndoFormatIndexName before " + index.toString());
            String namespacePrefix = namespace + "_";
            index.entrySet().forEach(entry -> {
                String oldIndexName = entry.getKey();
                if (oldIndexName.startsWith(namespacePrefix)) {
                    index.add(oldIndexName.substring(namespacePrefix.length()), entry.getValue());
                    index.remove(oldIndexName);
                } else {
                    throw new RuntimeException("The indexName must contain the " + namespace + " prefix, but it is " + entry.getKey());
                }
            });
            logger.info("UndoFormatIndexName after " + index.toString());
        }
        return index;
    }
}
