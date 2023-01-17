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
 */

package org.apache.skywalking.library.elasticsearch.bulk;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.Document;
import org.apache.skywalking.library.elasticsearch.response.Index;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.IndexRequestWrapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@RequiredArgsConstructor
@RunWith(Parameterized.class)
public class ITElasticSearch {

    @Parameterized.Parameters(name = "version: {0}, namespace: {1}")
    public static Collection<Object[]> versions() {
        return Arrays.asList(new Object[][] {
            {"6.3.2", ""},
            {"6.3.2", "test"},
            {"7.8.0", ""},
            {"7.8.0", "test"}
        });
    }

    private final String version;
    private final String namespace;

    private ElasticsearchContainer server;

    private ElasticSearchClient client;

    @Before
    public void before() throws Exception {
        server = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss")
                           .withTag(version)
        );
        server.start();

        client = new ElasticSearchClient(
            server.getHttpHostAddress(),
            "http", "", "", "test", "test",
            indexNameConverter(namespace), 500, 6000,
            0, 15
        );
        client.connect();
    }

    @After
    public void after() throws IOException {
        client.shutdown();
        server.stop();
    }

    @Test
    public void indexOperate() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("number_of_shards", 2);
        settings.put("number_of_replicas", 2);

        final Mappings mappings =
            Mappings.builder()
                    .type("type")
                    .properties(ImmutableMap.of(
                        "column1",
                        ImmutableMap.of("type", "text")
                    ))
                    .build();

        String indexName = "test_index_operate";
        client.createIndex(indexName, mappings, settings);
        Assert.assertTrue(client.isExistsIndex(indexName));

        Index index = client.getIndex(indexName).get();
        log.info(index.toString());

        Assert.assertEquals(
            "2",
            ((Map<String, ?>) index.getSettings().get("index")).get("number_of_shards")
        );
        Assert.assertEquals(
            "2",
            ((Map<String, ?>) index.getSettings().get("index")).get("number_of_replicas")
        );

        Assert.assertEquals(
            "text",
            ((Map<String, ?>) index.getMappings().getProperties().get("column1")).get("type")
        );
    }

    @Test
    public void documentOperate() {
        String id = String.valueOf(System.currentTimeMillis());

        Map<String, Object> builder = ImmutableMap.<String, Object>builder()
                                                  .put("user", "kimchy")
                                                  .put("post_date", "2009-11-15T14:12:12")
                                                  .put("message", "trying out Elasticsearch")
                                                  .build();

        String indexName = "test_document_operate";
        client.forceInsert(indexName, id, builder);

        Optional<Document> response = client.get(indexName, id);
        Assert.assertEquals("kimchy", response.get().getSource().get("user"));
        Assert.assertEquals("trying out Elasticsearch", response.get().getSource().get("message"));

        builder = ImmutableMap.<String, Object>builder().put("user", "pengys").build();
        client.forceUpdate(indexName, id, builder);

        response = client.get(indexName, id);
        Assert.assertEquals("pengys", response.get().getSource().get("user"));
        Assert.assertEquals("trying out Elasticsearch", response.get().getSource().get("message"));

        SearchBuilder sourceBuilder = Search.builder();
        sourceBuilder.query(Query.term("user", "pengys"));
        SearchResponse searchResponse = client.search(indexName, sourceBuilder.build());
        Assert.assertEquals("trying out Elasticsearch", searchResponse.getHits()
                                                                      .getHits()
                                                                      .iterator()
                                                                      .next()
                                                                      .getSource()
                                                                      .get("message"));
    }

    @Test
    public void templateOperate() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("number_of_shards", 1);
        settings.put("number_of_replicas", 0);
        settings.put("index.refresh_interval", "3s");
        settings.put("analysis.analyzer.oap_analyzer.type", "stop");

        Mappings mapping =
            Mappings.builder()
                    .type("type")
                    .properties(
                        ImmutableMap.of(
                            "name", ImmutableMap.of("type", "text")
                        )
                    )
                    .build();
        String indexName = "template_operate";

        client.createOrUpdateTemplate(indexName, settings, mapping, 0);

        Assert.assertTrue(client.isExistsTemplate(indexName));

        Map<String, Object> builder = ImmutableMap.of("name", "pengys");
        client.forceInsert(indexName + "-2019", "testid", builder);
        Index index = client.getIndex(indexName + "-2019").get();
        log.info(index.toString());

        Assert.assertEquals(
            "1",
            ((Map<String, Object>) index.getSettings().get("index")).get("number_of_shards")
        );
        Assert.assertEquals(
            "0",
            ((Map<String, ?>) index.getSettings().get("index")).get("number_of_replicas")
        );
        client.deleteTemplate(indexName);
        Assert.assertFalse(client.isExistsTemplate(indexName));
    }

    @Test
    public void bulk() {
        BulkProcessor bulkProcessor = client.createBulkProcessor(2000, 10, 2, 5 * 1024 * 1024);

        Map<String, String> source = new HashMap<>();
        source.put("column1", "value1");
        source.put("column2", "value2");

        for (int i = 0; i < 100; i++) {
            IndexRequestWrapper
                indexRequest =
                new IndexRequestWrapper("bulk_insert_test", "type", String.valueOf(i), source);
            bulkProcessor.add(indexRequest.getRequest());
        }

        bulkProcessor.flush();
    }

    @Test
    public void bulkPer_1KB() {
        BulkProcessor bulkProcessor = client.createBulkProcessor(2000, 10, 2, 1024);

        Map<String, String> source = new HashMap<>();
        source.put("column1", RandomStringUtils.randomAlphanumeric(1024));
        source.put("column2", "value2");

        for (int i = 0; i < 100; i++) {
            IndexRequestWrapper indexRequest = new IndexRequestWrapper(
                "bulk_insert_test6", "type", String.valueOf(i), source);
            bulkProcessor.add(indexRequest.getRequest());
        }

        bulkProcessor.flush();
    }

    @Test
    public void timeSeriesOperate() {
        final String indexName = "test_time_series_operate";
        final String timeSeriesIndexName = indexName + "-2019";
        final Mappings mapping =
            Mappings.builder()
                    .type("type")
                    .properties(ImmutableMap.of("name", ImmutableMap.of("type", "text")))
                    .build();

        client.createOrUpdateTemplate(indexName, new HashMap<>(), mapping, 0);

        Map<String, Object> builder = ImmutableMap.of("name", "pengys");
        client.forceInsert(timeSeriesIndexName, "testid", builder);

        Collection<String> indexes = client.retrievalIndexByAliases(indexName);
        Assert.assertEquals(1, indexes.size());
        String index = indexes.iterator().next();
        Assert.assertTrue(client.deleteByIndexName(index));
        Assert.assertFalse(client.isExistsIndex(timeSeriesIndexName));
        client.deleteTemplate(indexName);
    }

    private static Function<String, String> indexNameConverter(String namespace) {
        return indexName -> {
            if (StringUtil.isNotEmpty(namespace)) {
                return namespace + "_" + indexName;
            }
            return indexName;
        };
    }
}
