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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.Document;
import org.apache.skywalking.library.elasticsearch.response.Index;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.IndexRequestWrapper;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class ElasticSearchIT {

    public static Collection<Object[]> versions() {
        // noinspection resource
        return Arrays.asList(new Object[][]{
                {
                        new ElasticsearchContainer(
                                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss")
                                        .withTag("6.3.2")),
                        ""},
                {
                        new ElasticsearchContainer(
                                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss")
                                        .withTag("6.3.2")),
                        "test"},
                {
                        new ElasticsearchContainer(
                                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss")
                                        .withTag("7.8.0")),
                        ""},
                {
                        new ElasticsearchContainer(
                                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss")
                                        .withTag("7.8.0")),
                        "test"}
        });
    }

    @ParameterizedTest(name = "version: {0}")
    @MethodSource("versions")
    public void indexOperate(final ElasticsearchContainer server,
                             final String namespace) {
        server.start();

        final ElasticSearchClient client = new ElasticSearchClient(
                server.getHttpHostAddress(),
                "http", "", "", "test", "test",
                indexNameConverter(namespace), 500, 6000,
                0, 15
        );
        client.connect();

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
        Assertions.assertTrue(client.isExistsIndex(indexName));

        Index index = client.getIndex(indexName).get();
        log.info(index.toString());

        Assertions.assertEquals(
            "2",
            ((Map<String, ?>) index.getSettings().get("index")).get("number_of_shards")
        );
        Assertions.assertEquals(
            "2",
            ((Map<String, ?>) index.getSettings().get("index")).get("number_of_replicas")
        );

        Assertions.assertEquals(
            "text",
            ((Map<String, ?>) index.getMappings().getProperties().get("column1")).get("type")
        );

        client.shutdown();
        server.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versions")
    public void documentOperate(final ElasticsearchContainer server,
                                final String namespace) {
        server.start();

        final ElasticSearchClient client = new ElasticSearchClient(
                server.getHttpHostAddress(),
                "http", "", "", "test", "test",
                indexNameConverter(namespace), 500, 6000,
                0, 15
        );
        client.connect();

        String id = String.valueOf(System.currentTimeMillis());

        Map<String, Object> builder = ImmutableMap.<String, Object>builder()
                                                  .put("user", "kimchy")
                                                  .put("post_date", "2009-11-15T14:12:12")
                                                  .put("message", "trying out Elasticsearch")
                                                  .build();

        String indexName = "test_document_operate";
        client.forceInsert(indexName, id, builder);

        Optional<Document> response = client.get(indexName, id);
        Assertions.assertEquals("kimchy", response.get().getSource().get("user"));
        Assertions.assertEquals("trying out Elasticsearch", response.get().getSource().get("message"));

        builder = ImmutableMap.<String, Object>builder().put("user", "pengys").build();
        client.forceUpdate(indexName, id, builder);

        response = client.get(indexName, id);
        Assertions.assertEquals("pengys", response.get().getSource().get("user"));
        Assertions.assertEquals("trying out Elasticsearch", response.get().getSource().get("message"));

        SearchBuilder sourceBuilder = Search.builder();
        sourceBuilder.query(Query.term("user", "pengys"));
        SearchResponse searchResponse = client.search(indexName, sourceBuilder.build());
        Assertions.assertEquals("trying out Elasticsearch", searchResponse.getHits()
                                                                      .getHits()
                                                                      .iterator()
                                                                      .next()
                                                                      .getSource()
                                                                      .get("message"));
        client.deleteById(indexName, id);
        Assertions.assertFalse(client.existDoc(indexName, id));
        client.shutdown();
        server.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versions")
    public void templateOperate(final ElasticsearchContainer server,
                                final String namespace) {
        server.start();

        final ElasticSearchClient client = new ElasticSearchClient(
                server.getHttpHostAddress(),
                "http", "", "", "test", "test",
                indexNameConverter(namespace), 500, 6000,
                0, 15
        );
        client.connect();

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

        Assertions.assertTrue(client.isExistsTemplate(indexName));

        Map<String, Object> builder = ImmutableMap.of("name", "pengys");
        client.forceInsert(indexName + "-2019", "testid", builder);
        Index index = client.getIndex(indexName + "-2019").get();
        log.info(index.toString());

        Assertions.assertEquals(
            "1",
            ((Map<String, Object>) index.getSettings().get("index")).get("number_of_shards")
        );
        Assertions.assertEquals(
            "0",
            ((Map<String, ?>) index.getSettings().get("index")).get("number_of_replicas")
        );
        client.deleteTemplate(indexName);
        Assertions.assertFalse(client.isExistsTemplate(indexName));

        client.shutdown();
        server.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versions")
    public void bulk(final ElasticsearchContainer server,
                     final String namespace) {
        server.start();

        final ElasticSearchClient client = new ElasticSearchClient(
                server.getHttpHostAddress(),
                "http", "", "", "test", "test",
                indexNameConverter(namespace), 500, 6000,
                0, 15
        );
        client.connect();

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

        client.shutdown();
        server.stop();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versions")
    public void bulkPer_1KB(final ElasticsearchContainer server,
                            final String namespace) {
        server.start();

        final ElasticSearchClient client = new ElasticSearchClient(
                server.getHttpHostAddress(),
                "http", "", "", "test", "test",
                indexNameConverter(namespace), 500, 6000,
                0, 15
        );
        client.connect();

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("versions")
    public void timeSeriesOperate(final ElasticsearchContainer server,
                                  final String namespace) {
        server.start();

        final ElasticSearchClient client = new ElasticSearchClient(
                server.getHttpHostAddress(),
                "http", "", "", "test", "test",
                indexNameConverter(namespace), 500, 6000,
                0, 15
        );
        client.connect();

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
        Assertions.assertEquals(1, indexes.size());
        String index = indexes.iterator().next();
        Assertions.assertTrue(client.deleteByIndexName(index));
        Assertions.assertFalse(client.isExistsIndex(timeSeriesIndexName));
        client.deleteTemplate(indexName);

        client.shutdown();
        server.stop();
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
