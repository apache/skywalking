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

package org.apache.skywalking.library.elasticsearch;

import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.skywalking.library.elasticsearch.bulk.BulkProcessor;
import org.apache.skywalking.library.elasticsearch.bulk.BulkProcessorITExt;
import org.apache.skywalking.library.elasticsearch.requests.IndexRequest;
import org.apache.skywalking.library.elasticsearch.requests.UpdateRequest;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.QueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.BucketOrder;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.TermsAggregationBuilder;
import org.apache.skywalking.library.elasticsearch.response.Document;
import org.apache.skywalking.library.elasticsearch.response.Documents;
import org.apache.skywalking.library.elasticsearch.response.Index;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplate;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sends every request OAP's Elasticsearch storage sends, in the shape it sends it, to one server version, and checks
 * what the server stored and what the client parsed back. The client picks its request format by the version it
 * detects, so a nested class of {@link ElasticSearchIT} runs these on each supported version.
 *
 * <p>The cases follow OAP's callers: the storage installer (index templates with their alias and order, indices,
 * appended mappings, and comparing them back), the management and single-document writes (refreshed at once), the
 * persistence timer's bulk writes (inserts, updates, custom routing, a rejected item), and the query DAOs (every query
 * and aggregation shape they build, paging and sorting, routed reads, searching day indices of which some do not
 * exist, and scrolling with its context cleared).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ElasticSearchClientITBase {
    /** The mapping and document type OAP's storage passes; only Elasticsearch 6 keeps it. */
    private static final String TYPE = "type";
    private static final String OPENSEARCH = "OpenSearch";

    private final ElasticsearchContainer server;
    private final String distribution;
    private final String majorMinor;
    private ElasticSearch client;
    private ElasticSearchVersion version;

    /**
     * @param distribution {@code ElasticSearch} or {@code OpenSearch}
     * @param majorMinor   the server's version, as {@code major.minor}
     */
    protected ElasticSearchClientITBase(final ElasticsearchContainer server, final String distribution,
                                        final String majorMinor) {
        this.server = server.withEnv("cluster.routing.allocation.disk.threshold_enabled", "false");
        this.distribution = distribution;
        this.majorMinor = majorMinor;
    }

    @BeforeAll
    public void start() throws Exception {
        server.start();
        client = ElasticSearch.builder().endpoints(server.getHttpHostAddress()).build();
        version = client.connect().get(1, TimeUnit.MINUTES);
    }

    @AfterAll
    public void stop() {
        if (client != null) {
            client.close();
        }
        server.stop();
    }

    @Test
    public void detectsTheServerVersion() {
        assertEquals((distribution + " " + majorMinor).toLowerCase(Locale.ROOT),
                     version.toString().toLowerCase(Locale.ROOT));
    }

    /**
     * As the installer sets up a time-series model: a template for the model's day indices, read back for the same
     * comparison the installer makes on the next boot, then a day index created bare that takes the template's
     * mappings and alias, a column appended to that index, and the template updated with the column.
     */
    @Test
    public void keepsAnIndexTemplateThatDayIndicesFollow() {
        final String name = "tpl-segment";
        final Map<String, Object> properties = oapProperties();
        assertFalse(client.templates().exists(name));
        assertEquals(Optional.empty(), client.templates().get(name));

        assertTrue(client.templates().createOrUpdate(name, oapSettings("2"), mappings(properties), 7));
        assertTrue(client.templates().exists(name));
        final IndexTemplate template = client.templates().get(name).orElseThrow();
        assertEquals(name, template.getName());
        assertEquals(Collections.singletonList(name + "-*"), template.getIndexPatterns());
        assertEquals(Collections.singleton(name), template.getAliases().keySet());
        assertEquals(7, template.getOrder(), "the template's order (a composable template's priority)");
        assertEquals(properties, template.getMappings().getProperties());
        assertEquals(Collections.singleton("tags"), template.getMappings().getSource().getExcludes());
        assertEquals(oapSettings("2").get("index"), indexSettingsOf(template.getSettings()));

        final String day = name + "-20261118";
        assertTrue(client.index().create(day, null, null));
        final Index index = client.index().get(day).orElseThrow();
        assertEquals(asAnIndexReturnsThem(properties), index.getMappings().getProperties());
        assertEquals(Collections.singleton(name), index.getAliases().keySet());
        assertEquals(Collections.singleton(day), client.alias().indices(name).keySet());

        final Map<String, Object> column = ImmutableMap.of("new_column", ImmutableMap.of("type", "keyword"));
        assertTrue(client.index().putMapping(day, TYPE, mappings(column)));
        final Map<String, Object> appended = new HashMap<>(properties);
        appended.putAll(column);
        assertEquals(asAnIndexReturnsThem(appended),
                     client.index().get(day).orElseThrow().getMappings().getProperties());

        assertTrue(client.templates().createOrUpdate(name, oapSettings("2"), mappings(appended), 7));
        assertEquals(appended, client.templates().get(name).orElseThrow().getMappings().getProperties());

        assertTrue(client.templates().delete(name));
        assertFalse(client.templates().exists(name));
        assertEquals(Optional.empty(), client.templates().get(name));

        // An operator may order OAP's templates below others; a composable template cannot take a negative priority.
        final String negative = "tpl-negative-order";
        assertTrue(client.templates().createOrUpdate(negative, oapSettings("1"), mappings(properties), -1));
        final IndexTemplate negativeTemplate = client.templates().get(negative).orElseThrow();
        assertEquals(usesLegacyTemplates() ? -1 : 0, negativeTemplate.getOrder());
        assertEquals(!usesLegacyTemplates(), negativeTemplate.isComposable());
        assertTrue(client.templates().delete(negative));
        assertTrue(client.index().delete(day));
        assertFalse(client.index().exists(day));
        assertThrows(Exception.class, () -> client.alias().indices(name), "an alias without indices is missing");
    }

    /**
     * As the installer sets up a model stored in one index: created with its mappings and settings, read back for
     * the installer's comparison, and a column appended.
     */
    @Test
    public void keepsAnIndexWithItsMappingsAndSettings() {
        final String name = "idx-ui-template";
        assertFalse(client.index().exists(name));
        assertEquals(Optional.empty(), client.index().get(name));

        final Map<String, Object> properties = oapProperties();
        assertTrue(client.index().create(name, mappings(properties), oapSettings("1")));
        assertTrue(client.index().exists(name));
        final Index index = client.index().get(name).orElseThrow();
        assertEquals(asAnIndexReturnsThem(properties), index.getMappings().getProperties());
        assertEquals(Collections.singleton("tags"), index.getMappings().getSource().getExcludes());
        assertEquals(oapSettings("1").get("index"), indexSettingsOf(index.getSettings()));

        final Map<String, Object> column = ImmutableMap.of("disabled", ImmutableMap.of("type", "integer"));
        assertTrue(client.index().putMapping(name, TYPE, mappings(column)));
        final Map<String, Object> appended = new HashMap<>(properties);
        appended.putAll(column);
        assertEquals(asAnIndexReturnsThem(appended),
                     client.index().get(name).orElseThrow().getMappings().getProperties());

        assertTrue(client.index().delete(name));
        assertFalse(client.index().exists(name));
    }

    /**
     * As the management DAOs and the single-document writes use the documents: each write refreshes, so the next
     * search sees it at once. Ids carry what the client must escape in a path: spaces, as UI template ids do,
     * slashes, as runtime rule names and Base64 service ids do, and the other characters a path gives a meaning to.
     * A Base64 service id can also hold a {@code +}, which only a server reading it as a {@code +} keeps.
     */
    @Test
    public void writesAndReadsSingleDocumentsSearchableAtOnce() {
        final String name = "doc-management";
        assertTrue(client.index().create(name, mappings(oapProperties()), oapSettings("1")));
        final Map<String, Object> refresh = ImmutableMap.of("refresh", "true");
        final List<String> documentIds = new ArrayList<>(Arrays.asList(
            "an id", "runtimerule_otel-rules_aws-gateway/gateway-service", "a?b#c%d", "YT8/.1"));
        if (readsPlusInPathAsPlus()) {
            documentIds.add("b3JkZXI+c3Zj.1");
        }
        for (final String id : documentIds) {
            final Map<String, Object> doc = ImmutableMap.of("service_id", id, "latency", 10);
            client.documents().index(IndexRequest.builder().index(name).type(TYPE).id(id).doc(doc).build(),
                                     refresh);
            assertEquals(Collections.singletonList(id), ids(client.search(byIds(id), name)), id);
            assertTrue(client.documents().exists(name, TYPE, id), id);
            final Document read = client.documents().get(name, TYPE, id).orElseThrow();
            assertEquals(id, read.getId());
            assertEquals(doc, read.getSource(), id);

            final Map<String, Object> update = ImmutableMap.of("latency", 20);
            client.documents().update(UpdateRequest.builder().index(name).type(TYPE).id(id).doc(update).build(),
                                      refresh);
            assertEquals(ImmutableMap.of("service_id", id, "latency", 20),
                         client.documents().get(name, TYPE, id).orElseThrow().getSource(), id);
            assertEquals(Collections.singletonList(id), ids(client.search(
                Search.builder().query(Query.bool().must(Query.term("service_id", id)).must(Query.term("latency", 20)))
                      .build(), name)), id);

            client.documents().deleteById(name, TYPE, id, refresh);
            assertFalse(client.documents().exists(name, TYPE, id), id);
            assertEquals(Collections.emptyList(), ids(client.search(byIds(id), name)), id);
        }
        assertTrue(client.index().delete(name));
    }

    /**
     * As a read of rows by id across the day indices they may be in: ids missing from an index are left out.
     */
    @Test
    public void readsDocumentsOfSeveralIndicesById() {
        final String first = "mget-20261117";
        final String second = "mget-20261118";
        final Map<String, Object> refresh = ImmutableMap.of("refresh", "true");
        for (final String index : Arrays.asList(first, second)) {
            assertTrue(client.index().create(index, null, null));
            client.documents().index(
                IndexRequest.builder().index(index).type(TYPE).id("in-" + index).doc(ImmutableMap.of("day", index))
                            .build(), refresh);
        }
        final Map<String, List<String>> ids = new LinkedHashMap<>();
        ids.put(first, Arrays.asList("in-" + first, "in-" + second));
        ids.put(second, Arrays.asList("in-" + second, "missing"));
        final Documents documents = client.documents().mget(TYPE, ids).orElseThrow();
        final Map<String, Object> read = new HashMap<>();
        for (final Document document : documents) {
            read.put(document.getId(), document.getSource().get("day"));
        }
        assertEquals(ImmutableMap.of("in-" + first, first, "in-" + second, second), read);
        assertTrue(client.index().delete(first));
        assertTrue(client.index().delete(second));
    }

    /**
     * As the persistence timer writes: inserts and updates in one bulk, the inserts routed by a column as the
     * sharded mode does, read back by a search routed the same way. The routed rows share every searched value and
     * differ only in their routing over two shards, so only a search that applies its routing reads a subset.
     */
    @Test
    public void bulkWritesInsertsUpdatesAndRoutedInserts() throws Exception {
        final String name = "bulk-routed";
        // The server's default refresh interval, so the bulk's rows become searchable within a second.
        assertTrue(client.index().create(name, mappings(oapProperties()), settings("2", "1s")));
        final Map<String, Object> refresh = ImmutableMap.of("refresh", "true");
        client.documents().index(
            IndexRequest.builder().index(name).type(TYPE).id("existing").doc(ImmutableMap.of("latency", 1)).build(),
            refresh);

        final List<String> routings = new ArrayList<>();
        final BulkProcessor bulk = bulkProcessor(5 * 1024 * 1024);
        try {
            final List<CompletableFuture<Void>> written = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                routings.add("route-" + i);
                written.add(bulk.add(
                    IndexRequest.builder().index(name).type(TYPE).id("routed-" + i).routing(Optional.of("route-" + i))
                                .doc(ImmutableMap.of("service_id", "svc", "latency", i)).build()));
            }
            written.add(bulk.add(
                UpdateRequest.builder().index(name).type(TYPE).id("existing").doc(ImmutableMap.of("latency", 2))
                             .build()));
            bulk.flush();
            for (final CompletableFuture<Void> future : written) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            BulkProcessorITExt.close(bulk);
        }

        final List<String> all = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            all.add("routed-" + i);
        }
        await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> assertEquals(all, ids(client.search(
            sorted(Query.term("service_id", "svc")), new SearchParams().routing(routings), name))));
        final Set<String> readOneRouteAtATime = new TreeSet<>();
        boolean aRouteReadASubset = false;
        for (int i = 0; i < 10; i++) {
            final List<String> routed = ids(client.search(
                sorted(Query.term("service_id", "svc")), new SearchParams().routing("route-" + i), name));
            assertTrue(routed.contains("routed-" + i), "route-" + i + " reads " + routed);
            aRouteReadASubset |= routed.size() < all.size();
            readOneRouteAtATime.addAll(routed);
        }
        assertEquals(new TreeSet<>(all), readOneRouteAtATime);
        assertTrue(aRouteReadASubset, "a routed search read the rows of every shard");
        assertEquals(ImmutableMap.of("latency", 2), client.documents().get(name, TYPE, "existing").orElseThrow()
                                                          .getSource());
        assertTrue(client.index().delete(name));
    }

    /**
     * A bulk answered with HTTP 200 can still reject single items, e.g. when an index is blocked for writes
     * (apache/skywalking#14113): the rejected item's write fails, the other's succeeds, in one bulk request and in
     * one request per item ({@code batchOfBytes(1)}).
     */
    @Test
    public void failsOnlyTheBulkItemsTheServerRejects() throws Exception {
        final String normal = "bulk-item-failure-normal";
        final String blocked = "bulk-item-failure-blocked";
        assertTrue(client.index().create(normal, null, null));
        assertTrue(client.index().create(blocked, null, ImmutableMap.of("index.blocks.write", true)));
        for (final int batchOfBytes : new int[] {5 * 1024 * 1024, 1}) {
            final BulkProcessor bulk = bulkProcessor(batchOfBytes);
            try {
                final String normalId = "normal-" + batchOfBytes;
                final String blockedId = "blocked-" + batchOfBytes;
                final CompletableFuture<Void> normalWrite = bulk.add(
                    IndexRequest.builder().index(normal).type(TYPE).id(normalId).doc(ImmutableMap.of("key", "val"))
                                .build());
                final CompletableFuture<Void> blockedWrite = bulk.add(
                    IndexRequest.builder().index(blocked).type(TYPE).id(blockedId).doc(ImmutableMap.of("key", "val"))
                                .build());
                bulk.flush();
                // The scheduler's own periodical flush can race the explicit one, so wait on the futures themselves.
                normalWrite.get(30, TimeUnit.SECONDS);
                assertTrue(client.documents().get(normal, TYPE, normalId).isPresent());
                final String message = assertThrows(
                    ExecutionException.class, () -> blockedWrite.get(30, TimeUnit.SECONDS)).getCause().getMessage();
                assertTrue(message.contains("type=cluster_block_exception"), message);
                assertFalse(message.contains(blockedId), "the rejected document id must not be logged: " + message);
            } finally {
                BulkProcessorITExt.close(bulk);
            }
        }
    }

    /**
     * Every query shape the query DAOs build, each against rows that miss it by one value: term and terms, range
     * bounds (inclusive and exclusive), must, must-not and should, match on a keyword copied to an analyzed text
     * column and on a number, match-phrase, and ids.
     */
    @Test
    public void searchesWithEveryQueryShapeOAPBuilds() {
        final String name = writeRows("query-shapes");
        assertEquals(Arrays.asList("r1", "r3"), ids(client.search(sorted(Query.term("service_id", "svc-a")), name)));
        assertEquals(Arrays.asList("r1", "r2", "r3"),
                     ids(client.search(sorted(Query.terms("service_id", "svc-a", "svc-b")), name)));
        assertEquals(Arrays.asList("r2", "r4"),
                     ids(client.search(sorted(Query.terms("service_id", Arrays.asList("svc-b", "svc-c"))), name)));

        assertEquals(Arrays.asList("r2", "r3", "r4"),
                     ids(client.search(sorted(Query.range("latency").gte(20).lte(40)), name)));
        assertEquals(Collections.singletonList("r3"),
                     ids(client.search(sorted(Query.range("latency").gt(20).lt(40)), name)));

        assertEquals(Collections.singletonList("r3"), ids(client.search(sorted(
            Query.bool().must(Query.term("service_id", "svc-a")).mustNot(Query.term("latency", 10))), name)));
        assertEquals(Arrays.asList("r2", "r4"), ids(client.search(sorted(
            Query.bool().must(Query.bool().should(Query.term("service_id", "svc-b"))
                                          .should(Query.term("latency", 40)))), name)));

        assertEquals(Arrays.asList("r1", "r3"),
                     ids(client.search(sorted(Query.match("statement_match", "select orders")), name)));
        assertEquals(Collections.singletonList("r4"), ids(client.search(sorted(Query.match("is_error", 1)), name)));
        assertEquals(Collections.singletonList("r1"),
                     ids(client.search(sorted(Query.matchPhrase("statement_match", "from orders where")), name)));
        assertEquals(new TreeSet<>(Arrays.asList("r2", "r4")),
                     new TreeSet<>(ids(client.search(byIds("r2", "r4", "missing"), name))));

        final SearchResponse byTag = client.search(sorted(Query.term("tags", "http.method=POST")), name);
        assertEquals(Arrays.asList("r2", "r4"), ids(byTag));
        for (final SearchHit hit : byTag.getHits().getHits()) {
            assertFalse(hit.getSource().containsKey("tags"), "a column kept out of _source: " + hit.getSource());
            assertEquals("raw-" + hit.getId(), hit.getSource().get("tags_raw"));
        }
        final Document fetched = client.documents().mget(TYPE, ImmutableMap.of(name, Collections.singletonList("r2")))
                                       .orElseThrow().iterator().next();
        assertFalse(fetched.getSource().containsKey("tags"), "a column kept out of _source: " + fetched.getSource());
        assertTrue(client.index().delete(name));
    }

    /**
     * Pages of a sorted search, as the list queries read: by {@code from} and {@code size}, both orders, and only
     * the fields asked for when a query names them.
     */
    @Test
    public void pagesSortsAndFiltersTheSource() {
        final String name = writeRows("paging");
        final Function<Integer, List<String>> page = from -> ids(client.search(
            Search.builder().query(Query.range("latency").gte(0)).sort("latency", Sort.Order.DESC).from(from).size(2)
                  .build(), name));
        assertEquals(Arrays.asList("r4", "r3"), page.apply(0));
        assertEquals(Arrays.asList("r2", "r1"), page.apply(2));
        assertEquals(Collections.emptyList(), page.apply(4));
        assertEquals(Arrays.asList("r1", "r2", "r3", "r4"), ids(client.search(
            Search.builder().query(Query.range("latency").gte(0)).sort("latency", Sort.Order.ASC).size(10).build(),
            name)));

        final SearchResponse sources = client.search(
            Search.builder().query(Query.term("service_id", "svc-b")).source("service_id").source("latency").size(1)
                  .build(), name);
        assertEquals(ImmutableMap.of("service_id", "svc-b", "latency", 20),
                     sources.getHits().getHits().get(0).getSource());
        assertTrue(client.index().delete(name));
    }

    /**
     * Every aggregation the query DAOs build: entities ranked by the mean of a column in both orders with a
     * mapped execution hint (TopN), nested terms collected breadth first (topology), and the min and max of a
     * column, alone and ordering terms (trace and profiling reads).
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aggregatesAsOAPRanksAndGroups() {
        final String name = writeRows("aggregations");
        final Function<Boolean, SearchResponse> ranked = asc -> client.search(Search.builder().size(0).aggregation(
            Aggregation.terms("service_id").field("service_id").order(BucketOrder.aggregation("latency", asc))
                       .size(2).subAggregation(Aggregation.avg("latency").field("latency"))
                       .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                       .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)).build(), name);
        // Means: svc-a (10, 32) 21, svc-b 20, svc-c 40.
        assertEquals(Arrays.asList("svc-b=20.0", "svc-a=21.0"), buckets(ranked.apply(true), "service_id", "latency"));
        assertEquals(Arrays.asList("svc-c=40.0", "svc-a=21.0"), buckets(ranked.apply(false), "service_id", "latency"));

        final SearchResponse nested = client.search(Search.builder().size(0).aggregation(
            Aggregation.terms("service_id").field("service_id").size(1000)
                       .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                       .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)
                       .subAggregation(Aggregation.terms("is_error").field("is_error")
                                                  .executionHint(TermsAggregationBuilder.ExecutionHint.MAP)
                                                  .collectMode(TermsAggregationBuilder.CollectMode.BREADTH_FIRST)))
                                                         .build(), name);
        final Map<String, Set<String>> errorsByService = new LinkedHashMap<>();
        for (final Map<String, Object> bucket : bucketsOf(nested, "service_id")) {
            errorsByService.put(String.valueOf(bucket.get("key")), ((List<Map<String, Object>>) ((Map<String, Object>)
                bucket.get("is_error")).get("buckets")).stream().map(b -> String.valueOf(b.get("key")))
                                                       .collect(Collectors.toCollection(TreeSet::new)));
        }
        assertEquals(ImmutableMap.of("svc-a", Collections.singleton("0"), "svc-b", Collections.singleton("0"),
                                     "svc-c", Collections.singleton("1")), errorsByService);

        final SearchResponse range = client.search(Search.builder().query(Query.term("service_id", "svc-a")).size(0)
            .aggregation(Aggregation.min("min_latency").field("latency"))
            .aggregation(Aggregation.max("max_latency").field("latency")).build(), name);
        assertEquals(10.0, valueOf(range, "min_latency"));
        assertEquals(32.0, valueOf(range, "max_latency"));

        final SearchResponse latestFirst = client.search(Search.builder().size(0).aggregation(
            Aggregation.terms("service_id").field("service_id").size(2)
                       .order(BucketOrder.aggregation("max_latency", false))
                       .subAggregation(Aggregation.max("max_latency").field("latency"))).build(), name);
        assertEquals(Arrays.asList("svc-c=40.0", "svc-a=32.0"), buckets(latestFirst, "service_id", "max_latency"));
        assertTrue(client.index().delete(name));
    }

    /**
     * As OAP searches a time range's day indices, some of which do not exist: the missing ones are skipped. Without
     * those parameters the server refuses the search, which is why OAP sends them.
     */
    @Test
    public void searchesDayIndicesSkippingTheMissingOnes() {
        final String day = writeRows("days-20261118");
        final SearchParams skipMissing = new SearchParams().allowNoIndices(true).ignoreUnavailable(true)
                                                           .expandWildcards("open");
        assertEquals(Arrays.asList("r1", "r3"), ids(client.search(
            sorted(Query.term("service_id", "svc-a")), skipMissing.copy(), "days-20261117", day)));
        assertEquals(Collections.emptyList(), ids(client.search(
            sorted(Query.term("service_id", "svc-a")), skipMissing.copy(), "days-20261116", "days-20261117")));
        assertThrows(Exception.class, () -> client.search(
            sorted(Query.term("service_id", "svc-a")), new SearchParams(), "days-20261117", day));
        assertTrue(client.index().delete(day));
    }

    /**
     * As the trace and profiling reads page through more rows than one search returns: a scroll over two day
     * indices reads every row once, and clearing the context frees it on the server.
     */
    @Test
    public void scrollsThroughEveryRowAndClearsTheContext() {
        final List<String> expected = new ArrayList<>();
        for (final String day : Arrays.asList("scroll-20261117", "scroll-20261118")) {
            assertTrue(client.index().create(day, null, null));
            for (int i = 0; i < 5; i++) {
                final String id = day + "-" + i;
                client.documents().index(IndexRequest.builder().index(day).type(TYPE).id(id)
                                                     .doc(ImmutableMap.of("day", day)).build(),
                                         ImmutableMap.of("refresh", "true"));
                expected.add(id);
            }
        }
        final Duration retention = Duration.ofMinutes(1);
        SearchResponse response = client.search(
            Search.builder().size(3).build(),
            new SearchParams().scroll(retention), "scroll-20261117", "scroll-20261118");
        final List<String> read = new ArrayList<>();
        final String scrollId = response.getScrollId();
        while (!response.getHits().getHits().isEmpty()) {
            read.addAll(ids(response));
            assertTrue(read.size() <= expected.size(), "read past the rows: " + read);
            response = client.scroll(retention, response.getScrollId());
        }
        assertEquals(new TreeSet<>(expected), new TreeSet<>(read));
        assertEquals(expected.size(), read.size(), "each row once: " + read);
        assertTrue(client.deleteScrollContext(response.getScrollId() == null ? scrollId : response.getScrollId()));
        assertThrows(Exception.class, () -> client.scroll(retention, scrollId));
        assertTrue(client.index().delete("scroll-20261117"));
        assertTrue(client.index().delete("scroll-20261118"));
    }

    /**
     * Elasticsearch before 7.8 has only legacy index templates, which keep an order; composable ones keep a priority.
     */
    private boolean usesLegacyTemplates() {
        final String[] parts = majorMinor.split("\\.");
        final int major = Integer.parseInt(parts[0]);
        return !OPENSEARCH.equalsIgnoreCase(distribution)
            && (major < 7 || major == 7 && Integer.parseInt(parts[1]) < 8);
    }

    /**
     * Elasticsearch before 7.4 decodes a {@code +} in a path as a space.
     */
    private boolean readsPlusInPathAsPlus() {
        final String[] parts = majorMinor.split("\\.");
        final int major = Integer.parseInt(parts[0]);
        return OPENSEARCH.equalsIgnoreCase(distribution) || major > 7
            || major == 7 && Integer.parseInt(parts[1]) >= 4;
    }

    /**
     * Four rows, each matching a query shape the others miss by one value:
     * <pre>
     * id  service_id  latency  is_error  statement                           tags
     * r1  svc-a       10       0         select * from orders where id = ?   http.method=GET
     * r2  svc-b       20       0         update users set name = ?           http.method=POST
     * r3  svc-a       32       0         select count(*) from orders         http.method=GET
     * r4  svc-c       40       1         delete from carts                   http.method=POST
     * </pre>
     */
    private String writeRows(final String name) {
        assertTrue(client.index().create(name, mappings(oapProperties()), oapSettings("1")));
        final Object[][] rows = {
            {"r1", "svc-a", 10, 0, "select * from orders where id = ?"},
            {"r2", "svc-b", 20, 0, "update users set name = ?"},
            {"r3", "svc-a", 32, 0, "select count(*) from orders"},
            {"r4", "svc-c", 40, 1, "delete from carts"},
        };
        for (final Object[] row : rows) {
            final String method = "svc-b".equals(row[1]) || "svc-c".equals(row[1]) ? "POST" : "GET";
            client.documents().index(
                IndexRequest.builder().index(name).type(TYPE).id((String) row[0])
                            .doc(ImmutableMap.<String, Object>builder()
                                             .put("service_id", row[1]).put("latency", row[2])
                                             .put("is_error", row[3]).put("statement", row[4])
                                             .put("tags", "http.method=" + method).put("tags_raw", "raw-" + row[0])
                                             .build())
                            .build(), ImmutableMap.of("refresh", "true"));
        }
        return name;
    }

    /**
     * Columns the way OAP's installer maps them: a keyword copied to an analyzed text column for match queries,
     * numbers, a binary column, a column searched but kept out of {@code _source} (as segment tags are), and a column
     * stored but neither indexed nor kept in doc values. A template reads them back exactly as written, which the
     * installer's comparison of a stored template on the next boot relies on.
     */
    private static Map<String, Object> oapProperties() {
        final Map<String, Object> properties = new HashMap<>();
        properties.put("service_id", ImmutableMap.of("type", "keyword"));
        properties.put("latency", ImmutableMap.of("type", "long"));
        properties.put("is_error", ImmutableMap.of("type", "integer"));
        properties.put("statement", ImmutableMap.of("type", "keyword", "copy_to", "statement_match"));
        properties.put("statement_match", ImmutableMap.of("type", "text", "analyzer", "oap_analyzer"));
        properties.put("data_binary", ImmutableMap.of("type", "binary"));
        properties.put("tags", ImmutableMap.of("type", "keyword"));
        properties.put("tags_raw", ImmutableMap.of("type", "keyword", "index", false, "doc_values", false));
        return properties;
    }

    /**
     * The columns as an index reads them back: the server lists every {@code copy_to}. The installer compares an
     * existing day index by its column names only.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asAnIndexReturnsThem(final Map<String, Object> properties) {
        final Map<String, Object> read = new HashMap<>();
        properties.forEach((column, mapping) -> {
            final Map<String, Object> columnMapping = new HashMap<>((Map<String, Object>) mapping);
            columnMapping.computeIfPresent("copy_to", (key, to) -> Collections.singletonList(to));
            read.put(column, columnMapping);
        });
        return read;
    }

    private static Map<String, Object> oapSettings(final String shards) {
        return settings(shards, "31s");
    }

    private static Map<String, Object> settings(final String shards, final String refreshInterval) {
        return ImmutableMap.of("index", ImmutableMap.of(
            "number_of_shards", shards,
            "number_of_replicas", "0",
            "refresh_interval", refreshInterval,
            "analysis", ImmutableMap.of("analyzer", ImmutableMap.of("oap_analyzer", ImmutableMap.of("type", "stop")))
        ));
    }

    private static Mappings mappings(final Map<String, Object> properties) {
        final Mappings.Source source = new Mappings.Source();
        source.getExcludes().add("tags");
        return Mappings.builder().type(TYPE).properties(properties).source(source).build();
    }

    /**
     * The settings OAP wrote, from what the server returns: the server adds its own (creation date, uuid, version,
     * provided name), which OAP's comparison ignores.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> indexSettingsOf(final Map<String, Object> settings) {
        final Map<String, Object> index = (Map<String, Object>) settings.get("index");
        final Map<String, Object> written = new HashMap<>();
        for (final String key : Arrays.asList("number_of_shards", "number_of_replicas", "refresh_interval",
                                              "analysis")) {
            if (index.containsKey(key)) {
                written.put(key, index.get(key));
            }
        }
        return written;
    }

    private BulkProcessor bulkProcessor(final int batchOfBytes) {
        return BulkProcessor.builder()
                            .bulkActions(100)
                            .batchOfBytes(batchOfBytes)
                            .flushInterval(Duration.ofSeconds(30))
                            .concurrentRequests(2)
                            .bulkMetrics(new HistogramMetrics() {
                                @Override
                                public void observe(final double value) {
                                }
                            })
                            .build(new AtomicReference<>(client));
    }

    private static Search byIds(final String... ids) {
        return Search.builder().query(Query.ids(ids)).size(ids.length).build();
    }

    private static Search sorted(final QueryBuilder query) {
        final SearchBuilder search = Search.builder().query(query).size(100);
        return search.sort("latency", Sort.Order.ASC).build();
    }

    private static List<String> ids(final SearchResponse response) {
        return response.getHits().getHits().stream().map(SearchHit::getId).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> bucketsOf(final SearchResponse response, final String aggregation) {
        return (List<Map<String, Object>>) ((Map<String, Object>) response.getAggregations().get(aggregation))
            .get("buckets");
    }

    /**
     * Each bucket as {@code key=value} of the sub-aggregation named {@code value}.
     */
    @SuppressWarnings("unchecked")
    private static List<String> buckets(final SearchResponse response, final String aggregation,
                                        final String value) {
        return bucketsOf(response, aggregation).stream()
                                               .map(bucket -> bucket.get("key") + "="
                                                   + ((Map<String, Object>) bucket.get(value)).get("value"))
                                               .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static Object valueOf(final SearchResponse response, final String aggregation) {
        return ((Map<String, Object>) response.getAggregations().get(aggregation)).get("value");
    }
}
