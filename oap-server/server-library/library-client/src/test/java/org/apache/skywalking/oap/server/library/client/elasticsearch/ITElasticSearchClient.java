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
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author peng-yongsheng
 */
public class ITElasticSearchClient {

    private static final Logger logger = LoggerFactory.getLogger(ITElasticSearchClient.class);

    private ElasticSearchClient client;

    @Before
    public void before() throws IOException {
        final String esAddress = System.getProperty("elastic.search.address");
        client = new ElasticSearchClient(esAddress, "", "test", "test");
        client.connect();
    }

    @After
    public void after() throws IOException {
        client.shutdown();
    }

    @Test
    public void indexOperate() throws IOException {
        JsonObject settings = new JsonObject();
        settings.addProperty("number_of_shards", 2);
        settings.addProperty("number_of_replicas", 2);

        JsonObject mapping = new JsonObject();
        mapping.add("_doc", new JsonObject());

        JsonObject doc = mapping.getAsJsonObject("_doc");

        JsonObject properties = new JsonObject();
        doc.add("properties", properties);

        JsonObject column = new JsonObject();
        column.addProperty("type", "text");
        properties.add("column1", column);

        String indexName = "test_index_operate";
        client.createIndex(indexName, settings, doc);
        Assert.assertTrue(client.isExistsIndex(indexName));

        JsonObject index = client.getIndex(indexName);
        logger.info(index.toString());

        Assert.assertEquals(2, index.getAsJsonObject(indexName).getAsJsonObject("settings").getAsJsonObject("index").get("number_of_shards").getAsInt());
        Assert.assertEquals(2, index.getAsJsonObject(indexName).getAsJsonObject("settings").getAsJsonObject("index").get("number_of_replicas").getAsInt());

        Assert.assertEquals("text", index.getAsJsonObject(indexName).getAsJsonObject("mappings").getAsJsonObject("type").getAsJsonObject("properties").getAsJsonObject("column1").get("type").getAsString());

        Assert.assertTrue(client.deleteIndex(indexName));
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
        JsonObject settings = new JsonObject();
        settings.addProperty("number_of_shards", 1);
        settings.addProperty("number_of_replicas", 0);
        settings.addProperty("index.refresh_interval", "3s");
        settings.addProperty("analysis.analyzer.oap_analyzer.type", "stop");

        JsonObject mapping = new JsonObject();
        mapping.add("type", new JsonObject());
        JsonObject doc = mapping.getAsJsonObject("type");

        JsonObject properties = new JsonObject();
        doc.add("properties", properties);

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

        JsonObject index = client.getIndex(indexName + "-2019");
        logger.info(index.toString());
        Assert.assertEquals(1, index.getAsJsonObject(indexName + "-2019").getAsJsonObject("settings").getAsJsonObject("index").get("number_of_shards").getAsInt());
        Assert.assertEquals(0, index.getAsJsonObject(indexName + "-2019").getAsJsonObject("settings").getAsJsonObject("index").get("number_of_replicas").getAsInt());

        client.deleteTemplate(indexName);
        Assert.assertFalse(client.isExistsTemplate(indexName));
    }

    @Test
    public void bulk() throws InterruptedException {
        BulkProcessor bulkProcessor = client.createBulkProcessor(2000, 200, 10, 2);

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
}
