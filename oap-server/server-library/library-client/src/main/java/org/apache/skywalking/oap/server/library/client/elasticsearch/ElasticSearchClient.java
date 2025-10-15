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

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.response.Documents;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.library.elasticsearch.ElasticSearch;
import org.apache.skywalking.library.elasticsearch.ElasticSearchBuilder;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.bulk.BulkProcessor;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.response.Document;
import org.apache.skywalking.library.elasticsearch.response.Index;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplate;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.util.HealthChecker;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * ElasticSearchClient connects to the ES server by using ES client APIs.
 */
@Slf4j
public class ElasticSearchClient implements Client, HealthCheckable {
    public static final String TYPE = "type";

    private final String clusterNodes;

    private final String protocol;

    private final String trustStorePath;

    @Setter
    private volatile String trustStorePass;

    @Setter
    private volatile String user;

    @Setter
    private volatile String password;

    private final Function<String, String> indexNameConverter;

    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();

    private final int connectTimeout;

    private final int socketTimeout;

    private final int responseTimeout;

    private final int numHttpClientThread;

    private final AtomicReference<ElasticSearch> es = new AtomicReference<>();

    private final ModuleManager moduleManager;
    private HistogramMetrics singleWriteHistogram;
    private HistogramMetrics singleUpdateHistogram;
    private HistogramMetrics singleDeleteHistogram;
    private HistogramMetrics bulkWriteHistogram;

    public ElasticSearchClient(ModuleManager moduleManager,
                               String clusterNodes,
                               String protocol,
                               String trustStorePath,
                               String trustStorePass,
                               String user,
                               String password,
                               Function<String, String> indexNameConverter,
                               int connectTimeout,
                               int socketTimeout,
                               int responseTimeout,
                               int numHttpClientThread) {
        this.moduleManager = moduleManager;
        this.clusterNodes = clusterNodes;
        this.protocol = protocol;
        this.trustStorePath = trustStorePath;
        this.trustStorePass = trustStorePass;
        this.user = user;
        this.password = password;
        this.indexNameConverter = indexNameConverter;
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
        this.responseTimeout = responseTimeout;
        this.numHttpClientThread = numHttpClientThread;
    }

    @Override
    public void connect() {
        initTelemetry();
        final ElasticSearch oldOne = es.get();

        final ElasticSearchBuilder cb =
            ElasticSearch
                .builder()
                .endpoints(clusterNodes.split(","))
                .protocol(protocol)
                .connectTimeout(connectTimeout)
                .responseTimeout(responseTimeout)
                .socketTimeout(socketTimeout)
                .numHttpClientThread(numHttpClientThread)
                .healthyListener(healthy -> {
                    if (healthy) {
                        healthChecker.health();
                    } else {
                        healthChecker.unHealth("No healthy endpoint");
                    }
                });

        if (!Strings.isNullOrEmpty(trustStorePath)) {
            cb.trustStorePath(trustStorePath);
        }
        if (!Strings.isNullOrEmpty(trustStorePass)) {
            cb.trustStorePass(trustStorePass);
        }
        if (!Strings.isNullOrEmpty(user)) {
            cb.username(user);
        }
        if (!Strings.isNullOrEmpty(password)) {
            cb.password(password);
        }

        final var newOne = cb.build();
        final var stopWatch = Stopwatch.createStarted();
        // Only swap the old / new after the new one established a new connection.
        final CompletableFuture<ElasticSearchVersion> f = newOne.connect();
        f.whenComplete((ignored, exception) -> {
            stopWatch.stop();
            if (exception != null) {
                log.error("Failed to recreate ElasticSearch client based on config", exception);
                return;
            }
            final var connectingTime = stopWatch.elapsed(TimeUnit.MILLISECONDS);
            if (connectingTime > 1000) {
                log.warn(
                    "Connecting to ElasticSearch took {} ms, which is longer than expected and can impact performance.",
                    connectingTime);
            }
            if (es.compareAndSet(oldOne, newOne)) {
                oldOne.close();
            } else {
                newOne.close();
            }
        });
        f.join();
    }

    @Override
    public void shutdown() {
        es.get().close();
    }

    @Override
    public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }

    public boolean createIndex(String indexName) {
        return createIndex(indexName, null, null);
    }

    public boolean createIndex(String indexName,
                               Mappings mappings,
                               Map<String, ?> settings) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().index().create(indexName, mappings, settings);
    }

    public boolean updateIndexMapping(String indexName, Mappings mapping) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().index().putMapping(indexName, TYPE, mapping);
    }

    public Optional<Index> getIndex(String indexName) {
        if (StringUtil.isBlank(indexName)) {
            return Optional.empty();
        }
        indexName = indexNameConverter.apply(indexName);
        return es.get().index().get(indexName);
    }

    public Collection<String> retrievalIndexByAliases(String alias) {
        alias = indexNameConverter.apply(alias);

        return es.get().alias().indices(alias).keySet();
    }

    /**
     * If your indexName is retrieved from elasticsearch through {@link
     * #retrievalIndexByAliases(String)} or some other method and it already contains namespace.
     * Then you should delete the index by this method, this method will no longer concatenate
     * namespace.
     *
     * https://github.com/apache/skywalking/pull/3017
     */
    public boolean deleteByIndexName(String indexName) {
        return es.get().index().delete(indexName);
    }

    public boolean isExistsIndex(String indexName) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().index().exists(indexName);
    }

    public Optional<IndexTemplate> getTemplate(String name) {
        name = indexNameConverter.apply(name);

        return es.get().templates().get(name);
    }

    public boolean isExistsTemplate(String indexName) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().templates().exists(indexName);
    }

    public boolean createOrUpdateTemplate(String indexName, Map<String, Object> settings,
                                          Mappings mapping, int order) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().templates().createOrUpdate(indexName, settings, mapping, order);
    }

    public boolean deleteTemplate(String indexName) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().templates().delete(indexName);
    }

    public SearchResponse search(Supplier<String[]> indices, Search search) {
        final String[] indexNames =
            Arrays.stream(indices.get())
                  .map(indexNameConverter)
                  .toArray(String[]::new);
        final SearchParams params = new SearchParams()
            .allowNoIndices(true)
            .ignoreUnavailable(true)
            .expandWildcards("open");
        return es.get().search(
            search,
            params,
            indexNames);
    }

    public SearchResponse search(String indexName, Search search) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().search(search, indexName);
    }

    public SearchResponse search(String indexName, Search search, SearchParams params) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().search(search, params, indexName);
    }

    public SearchResponse scroll(Duration contextRetention, String scrollId) {
        return es.get().scroll(contextRetention, scrollId);
    }

    public boolean deleteScrollContextQuietly(String scrollId) {
        try {
            return es.get().deleteScrollContext(scrollId);
        } catch (Exception e) {
            log.warn("Failed to delete scroll context: {}", scrollId, e);
            return false;
        }
    }

    public Optional<Document> get(String indexName, String id) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().documents().get(indexName, TYPE, id);
    }

    public boolean existDoc(String indexName, String id) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().documents().exists(indexName, TYPE, id);
    }

    /**
     * Provide to get documents from multi indices by IDs.
     * @param indexIds key: indexName, value: ids list
     * @return Documents
     * @since 9.2.0
     */
    public Optional<Documents> ids(Map<String, List<String>> indexIds) {
        Map<String, List<String>> map = new HashMap<>();
        indexIds.forEach((indexName, ids) -> {
            map.put(indexNameConverter.apply(indexName), ids);
        });
        return es.get().documents().mget(TYPE, map);
    }

    /**
     * Search by ids with index alias, when can not locate the physical index.
     * Otherwise, recommend use method {@link #ids}
     * @param indexName Index alias name or physical name
     * @param ids ID list
     * @return SearchResponse
     * @since 9.2.0 this method was ids
     */
    public SearchResponse searchIDs(String indexName, Iterable<String> ids) {
        indexName = indexNameConverter.apply(indexName);

        return es.get().search(Search.builder()
                                     .size(Iterables.size(ids))
                                     .query(Query.ids(ids))
                                     .build(), indexName);
    }

    public void forceInsert(String indexName, String id, Map<String, Object> source) {
        try (HistogramMetrics.Timer timer = singleWriteHistogram.createTimer()) {
            IndexRequestWrapper wrapper = prepareInsert(indexName, id, source);
            Map<String, Object> params = ImmutableMap.of("refresh", "true");
            es.get().documents().index(wrapper.getRequest(), params);
        }
    }

    public void forceUpdate(String indexName, String id, Map<String, Object> source) {
        try (HistogramMetrics.Timer timer = singleUpdateHistogram.createTimer()) {
            UpdateRequestWrapper wrapper = prepareUpdate(indexName, id, source);
            Map<String, Object> params = ImmutableMap.of("refresh", "true");
            es.get().documents().update(wrapper.getRequest(), params);
        }
    }

    public void deleteById(String indexName, String id) {
        try (HistogramMetrics.Timer timer = singleDeleteHistogram.createTimer()) {
            indexName = indexNameConverter.apply(indexName);
            Map<String, Object> params = ImmutableMap.of("refresh", "true");
            es.get().documents().deleteById(indexName, TYPE, id, params);
        }
    }

    public IndexRequestWrapper prepareInsert(String indexName, String id,
                                             Map<String, Object> source) {
        return prepareInsert(indexName, id, Optional.empty(), source);
    }

    public IndexRequestWrapper prepareInsert(String indexName, String id, Optional<String> routingValue,
                                             Map<String, Object> source) {
        indexName = indexNameConverter.apply(indexName);
        return new IndexRequestWrapper(indexName, TYPE, id, routingValue, source);
    }

    public UpdateRequestWrapper prepareUpdate(String indexName, String id,
                                              Map<String, Object> source) {
        indexName = indexNameConverter.apply(indexName);
        return new UpdateRequestWrapper(indexName, TYPE, id, source);
    }

    public BulkProcessor createBulkProcessor(int bulkActions,
                                             int flushInterval,
                                             int concurrentRequests,
                                             int batchOfBytes) {
        return BulkProcessor.builder()
                            .bulkActions(bulkActions)
                            .batchOfBytes(batchOfBytes)
                            .flushInterval(Duration.ofSeconds(flushInterval))
                            .concurrentRequests(concurrentRequests)
                            .bulkMetrics(bulkWriteHistogram)
                            .build(es);
    }

    public String formatIndexName(String indexName) {
        return indexNameConverter.apply(indexName);
    }

    private void initTelemetry() {
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        if (singleWriteHistogram == null) {
            singleWriteHistogram = metricsCreator.createHistogramMetric(
                "elasticsearch_write_latency",
                "Elasticsearch write/update/delete latency in seconds, bulk_write include write/update",
                new MetricsTag.Keys("operation"),
                new MetricsTag.Values("single_write"),
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
            );
        }
        if (singleUpdateHistogram == null) {
            singleUpdateHistogram = metricsCreator.createHistogramMetric(
                "elasticsearch_write_latency",
                "Elasticsearch write/update/delete latency in seconds, bulk_write include write/update",
                new MetricsTag.Keys("operation"),
                new MetricsTag.Values("single_update"),
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
            );
        }
        if (singleDeleteHistogram == null) {
            singleDeleteHistogram = metricsCreator.createHistogramMetric(
                "elasticsearch_write_latency",
                "Elasticsearch write/update/delete latency in seconds, bulk_write include write/update",
                new MetricsTag.Keys("operation"),
                new MetricsTag.Values("single_delete"),
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
            );
        }
        if (bulkWriteHistogram == null) {
            bulkWriteHistogram = metricsCreator.createHistogramMetric(
                "elasticsearch_write_latency",
                "Elasticsearch write/update/delete latency in seconds, bulk_write include write/update",
                new MetricsTag.Keys("operation"),
                new MetricsTag.Values("bulk_write"),
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
            );
        }
    }
}
