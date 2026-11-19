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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.cache.TopNCacheReadCommand;
import org.apache.skywalking.oap.server.core.analysis.manual.database.TopNDatabaseStatement;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgHistogramFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgLabeledFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.ApdexMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.CPMMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.PercentileMetrics2;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.AttrCondition;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads metrics the way the metrics query service, the top-N ranking and the top-N records do on Elasticsearch and
 * OpenSearch, over ranges that cross midnight, so over two daily indices. The metrics are the classes OAP generates
 * from the shipped {@code core.oal} and MAL meters of their function classes, so each value column is read under the
 * name its function stores it by, which logic sharding replaces with a legacy name for some: a long
 * ({@code service_instance_resp_time}, minute and hour), an int ({@code service_apdex}), the percentile labels
 * ({@code service_percentile}), a MAL histogram, MAL labeled meters, the call rate whose entities are listed, and MAL
 * {@code vm.yaml} meters ranked across hosts. A nested class of a server's IT names the server and the storage mode.
 *
 * <p>The edge cases: a minute and an hour without a point, read as empty, beside another instance that has every
 * point; a labeled row without one of the labels; points just outside each range that is filtered by time; metrics of
 * the same function, which share an index and are told apart by {@code metric_table}, on the same entities and
 * minutes; more labeled rows than the read of every entity returns; entities ranked by the mean of the whole range,
 * which no single minute or day ranks the same way; endpoints of another service; statements on and just outside the
 * bounds of the range, of another database, and cache commands of the same entity, which share an index in the
 * default mode and are told apart by {@code record_table}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class MetricsQueryITBase {
    private static final String LATENCY_HISTOGRAM = "meter_checkout_latency_histogram";
    private static final String CPU = "meter_vm_cpu_total_percentage";
    private static final String MEMORY = "meter_vm_memory_used";
    private static final String LOAD = "meter_vm_load";
    private static final String INSTANCE_RESP_TIME = "service_instance_resp_time";
    private static final String RESP_TIME = "service_resp_time";
    private static final String MQ_LATENCY = "service_mq_consume_latency";
    private static final String ENDPOINT_RESP_TIME = "endpoint_resp_time";
    private static final String APDEX = "service_apdex";
    private static final String PERCENTILE = "service_percentile";
    private static final String CPM = "service_cpm";
    private static final String ENDPOINT_CPM = "endpoint_cpm";
    private static final String POOL_SIZE = "meter_pool_size";
    private static final String POOL_WAIT = "meter_pool_wait";
    private static final String SERVICE = "checkout";
    private static final String OTHER_SERVICE = "payment";
    private static final long[] HISTOGRAM_BUCKETS = {0, 50, 100};
    /** Minute from midnight, then the count of each bucket. Minute -1 has no histogram. */
    private static final long[][] HISTOGRAMS = {
        {-2, 3, 5, 1},
        {0, 4, 6, 2},
        {1, 5, 7, 3},
    };
    /**
     * Host, then the CPU percentage of each minute from -3 to 2. web-1 is steady at 50; batch-1 is busy until
     * midnight and backup-1 from midnight, each with one quiet minute on the other day, so each tops one day and
     * the whole range averages them to 46 and 47. A missing minute is -1.
     */
    private static final Object[][] CPU_MINUTES = {
        {"web-1", new long[] {-1, -1, 50, 50, 50, 50}},
        {"batch-1", new long[] {60, 60, 60, 5, -1, -1}},
        {"backup-1", new long[] {-1, -1, 5, 62, 62, 62}},
    };
    /**
     * Hosts on each side of midnight that top web-1 on their own day, more than the 11 buckets a shard returns for a
     * top 1 by default. web-1 is steady at 50 from minute -3 to 2; each day host is 60 until midnight and 5 at
     * minute 0, each night host 5 at minute -1 and 62 from midnight, so they average 46 and 47.
     */
    private static final int HOSTS_PER_DAY = 12;
    private static final long GIB = 1L << 30;
    /** Host, then the used memory in bytes of each minute from -2 to 0: db-1 averages 26 GiB, web-1 4 GiB. */
    private static final Object[][] MEMORY_MINUTES = {
        {"db-1", new long[] {30 * GIB, 30 * GIB, 18 * GIB}},
        {"web-1", new long[] {-1, 3 * GIB, 5 * GIB}},
    };
    /** Minute from midnight, then checkout-1's response time. Minute -1 has none; checkout-2 has one every minute. */
    private static final long[][] INSTANCE_MINUTES = {{-3, 110}, {-2, 120}, {0, 140}, {1, 150}, {2, 160}};
    /** Hour from midnight, then checkout-1's response time. Hour -1 has none; checkout-2 has one every hour. */
    private static final long[][] INSTANCE_HOURS = {{-2, 1000}, {0, 3000}, {1, 4000}};
    /** Service, then its apdex at minutes -1, 0 and 1; -1 is no point. Over minutes -1 and 0: 10000, 5000, 2500. */
    private static final Object[][] APDEX_MINUTES = {
        {SERVICE, new long[] {10000, -1, 5000}},
        {OTHER_SERVICE, new long[] {7500, 2500, -1}},
        {"auth", new long[] {-1, 2500, 10000}},
    };
    /**
     * Service, its layer (attr0), then its response time at minutes -1 and 0. The means, 50, 60 and 10, rank them
     * unlike either minute does. resp-c also has 1000 at minutes -2 and 1, just outside the range ranked, and resp-a
     * has 1 at both minutes in {@code service_mq_consume_latency}, a metric of the same function.
     */
    private static final Object[][] RANKED_SERVICES = {
        {"resp-a", "GENERAL", new long[] {10, 90}},
        {"resp-b", "GENERAL", new long[] {60, 60}},
        {"resp-c", "MESH", new long[] {5, 15}},
    };
    /** Endpoint of checkout, then its response time at minutes -1 and 0; payment's /order is 500 at both. */
    private static final Object[][] RANKED_ENDPOINTS = {
        {"/order", new long[] {10, 90}},
        {"/pay", new long[] {60, 60}},
        {"/cart", new long[] {5, 15}},
    };
    /**
     * Service, then the minutes it has a call rate at. The range listed is minutes -3 to 2: cpm-e and cpm-f are just
     * outside it, cpm-g and cpm-h on its bounds, and an endpoint of checkout has {@code endpoint_cpm} at minute 2.
     */
    private static final Object[][] CPM_MINUTES = {
        {"cpm-a", new long[] {-3, -2}},
        {"cpm-b", new long[] {-1}},
        {"cpm-c", new long[] {-3, 0}},
        {"cpm-d", new long[] {1}},
        {"cpm-e", new long[] {3}},
        {"cpm-f", new long[] {-4}},
        {"cpm-g", new long[] {2}},
        {"cpm-h", new long[] {-3}},
    };
    /**
     * Pool, minute, then its active and idle connections; -1 is a label the row does not have. The range read is
     * minutes -1 and 0: pool-3 at minute -2 and pool-0 at minute 1 are just outside it.
     */
    private static final long[][] POOLS = {
        {0, -1, 10, 100},
        {1, -1, 20, 200},
        {2, -1, 30, -1},
        {0, 0, 11, 101},
        {1, 0, 21, 201},
        {3, -2, 98, 998},
        {0, 1, 99, 999},
    };
    /** Waiting pools at each of minutes -1 and 0: more rows than a read of every entity returns. */
    private static final int WAITING_POOLS = 6;
    private static final String DATABASE = "orders-db:3306";
    /**
     * Second from midnight, latency in ms. The range read is seconds -120 to 119, so the first and last statements
     * sit on its bounds.
     */
    private static final long[][] STATEMENTS = {
        {-120, 600},
        {-110, 1200},
        {-80, 300},
        {-40, 900},
        {15, 1500},
        {45, 100},
        {90, 700},
        {119, 50},
    };
    /** The slowest and the fastest statements of the database, each a second outside the range. */
    private static final long[][] STATEMENTS_OUTSIDE = {{-121, 5000}, {120, 10}};

    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private MetricsQueryEsDAO metricsDAO;
    private AggregationQueryEsDAO aggregationDAO;
    private RecordsQueryEsDAO recordsDAO;
    private final Map<String, Model> models = new HashMap<>();
    private final Map<String, Long> waiting = new TreeMap<>();

    MetricsQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeMetrics() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        metricsDAO = new MetricsQueryEsDAO(support.getClient());
        aggregationDAO = new AggregationQueryEsDAO(support.getClient());
        recordsDAO = new RecordsQueryEsDAO(support.getClient());
        MeterEntity.setNamingControl(new NamingControl(70, 100, 150, new EndpointNameGrouping()));
        installModels();

        final List<Metrics> histograms = new ArrayList<>();
        for (final long[] point : HISTOGRAMS) {
            final HistogramMeter meter = new HistogramMeter();
            meter.setTimeBucket(minuteBucket(point[0]));
            meter.accept(MeterEntity.newService(SERVICE, Layer.GENERAL),
                         new BucketedValues(HISTOGRAM_BUCKETS, Arrays.copyOfRange(point, 1, point.length)));
            meter.calculate();
            histograms.add(meter);
        }
        support.writeMetrics(model(LATENCY_HISTOGRAM, DownSampling.Minute),
                             new AvgHistogramFunction.AvgHistogramFunctionBuilder(), histograms);

        writeHostMetric(CPU, CPU_MINUTES, -3);
        writeHostMetric(MEMORY, MEMORY_MINUTES, -2);
        final List<Object[]> loads = new ArrayList<>();
        loads.add(new Object[] {"web-1", new long[] {50, 50, 50, 50, 50, 50}});
        for (int i = 0; i < HOSTS_PER_DAY; i++) {
            loads.add(new Object[] {"day-" + i, new long[] {60, 60, 60, 5, -1, -1}});
            loads.add(new Object[] {"night-" + i, new long[] {-1, -1, 5, 62, 62, 62}});
        }
        writeHostMetric(LOAD, loads.toArray(new Object[0][]), -3);
        writeInstanceResponseTimes();
        writeApdex();
        writePercentiles();
        writeRankedResponseTimes();
        writeCallRates();
        writePools();
        writeStatements();
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * Every minute of the range in minute order, each from whichever daily index holds it, and the minute none holds
     * as empty.
     */
    @Test
    public void readsEveryMinuteOfARangeAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("110", "120", "empty", "140", "150", "160"),
                     values(INSTANCE_RESP_TIME, instance("checkout-1"), minutes(-3, 2)));
        assertEquals(Arrays.asList("empty", "empty"),
                     values(INSTANCE_RESP_TIME, instance("checkout-9"), minutes(-1, 0)));
    }

    @Test
    public void readsEveryHourOfARangeAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("1000", "empty", "3000", "4000"),
                     values(INSTANCE_RESP_TIME, instance("checkout-1"), hours(-2, 1)));
    }

    @Test
    public void readsTheIntValuesOfAMetric() throws Exception {
        assertEquals(Arrays.asList("10000", "empty", "5000"), values(APDEX, service(SERVICE), minutes(-1, 1)));
    }

    /**
     * Each percentile label over every minute of the range, the minute without a point as empty.
     */
    @Test
    public void readsEveryLabelOfALabeledMetricAcrossMidnight() throws Exception {
        final Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("{p=50}", Arrays.asList("500", "empty", "1000"));
        expected.put("{p=75}", Arrays.asList("750", "empty", "1500"));
        expected.put("{p=90}", Arrays.asList("900", "empty", "1800"));
        expected.put("{p=95}", Arrays.asList("950", "empty", "1900"));
        expected.put("{p=99}", Arrays.asList("990", "empty", "1980"));
        assertEquals(expected, labeled(Collections.emptyList()));
    }

    @Test
    public void readsTheLabelsAskedFor() throws Exception {
        final Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("{p=50}", Arrays.asList("500", "empty", "1000"));
        expected.put("{p=99}", Arrays.asList("990", "empty", "1980"));
        assertEquals(expected, labeled(Collections.singletonList(new KeyValue("p", "50,99"))));
        assertEquals(Collections.emptyMap(), labeled(Collections.singletonList(new KeyValue("p", "42"))));
    }

    /**
     * One column per minute of the range, in minute order, each from whichever daily index holds it, and an empty
     * column for the minute none holds.
     */
    @Test
    public void readsOneHeatMapColumnPerMinuteAcrossMidnight() throws Exception {
        final HeatMap heatMap = metricsDAO.readHeatMap(
            condition(LATENCY_HISTOGRAM, service(SERVICE)), valueColumn(LATENCY_HISTOGRAM), minutes(-2, 1));
        assertEquals(
            Arrays.asList(Arrays.asList(3L, 5L, 1L), Arrays.asList(0L, 0L, 0L), Arrays.asList(4L, 6L, 2L),
                          Arrays.asList(5L, 7L, 3L)),
            heatMap.getValues().stream().map(HeatMap.HeatMapColumn::getValues).collect(Collectors.toList()));
        assertEquals("[{\"min\":\"0\",\"max\":\"50\"},{\"min\":\"50\",\"max\":\"100\"},"
                         + "{\"min\":\"100\",\"max\":\"infinite+\"}]",
                     new Gson().toJson(heatMap.getBuckets()));
    }

    /**
     * Every document of the metric in the range, from both daily indices, each under every label; a document without
     * a label reads as empty under it.
     */
    @Test
    public void readsTheLabeledValuesOfEveryEntityAcrossMidnight() throws Exception {
        final Map<String, Map<String, String>> expected = new TreeMap<>();
        expected.put("{pool=active}", new TreeMap<>());
        expected.put("{pool=idle}", new TreeMap<>());
        for (final long[] pool : POOLS) {
            if (pool[1] < -1 || pool[1] > 0) {
                continue;
            }
            final String id = poolRowId(POOL_SIZE, pool[0], pool[1]);
            expected.get("{pool=active}").put(id, String.valueOf(pool[2]));
            expected.get("{pool=idle}").put(id, pool[3] < 0 ? "empty" : String.valueOf(pool[3]));
        }
        assertEquals(expected, everyEntity(POOL_SIZE, Collections.emptyList()));

        expected.remove("{pool=active}");
        assertEquals(expected, everyEntity(POOL_SIZE, Collections.singletonList(new KeyValue("pool", "idle"))));
    }

    @Test
    public void capsTheLabeledValuesOfEveryEntity() throws Exception {
        final Map<String, Map<String, String>> read = everyEntity(POOL_WAIT, Collections.emptyList());
        assertEquals(Collections.singleton("{pool=waiting}"), read.keySet());
        final Map<String, String> rows = read.get("{pool=waiting}");
        assertEquals(IMetricsQueryDAO.METRICS_VALUES_WITHOUT_ENTITY_LIMIT, rows.size());
        for (final Map.Entry<String, String> row : rows.entrySet()) {
            assertEquals(String.valueOf(waiting.get(row.getKey())), row.getValue(), row.getKey());
        }
    }

    /**
     * The entities with a point in the range, by their latest point, newest first: the latest of one is on the other
     * day of the range, and the ones just outside the range or of another metric of the same function are not.
     */
    @Test
    public void listsTheEntitiesOfARangeNewestFirstAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("cpm-g", "cpm-d", "cpm-c", "cpm-b", "cpm-a", "cpm-h"), entities(minutes(-3, 2), 10));
        assertEquals(Arrays.asList("cpm-g", "cpm-d", "cpm-c"), entities(minutes(-3, 2), 3));
        assertEquals(Collections.emptyList(), entities(minutes(-6, -5), 10));
    }

    /**
     * Each host's mean over the whole range, not the mean of its two daily means (32 for batch-1, 33 for backup-1).
     */
    @Test
    public void ranksTheMeanOfTheWholeRangeAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("web-1=50", "backup-1=47", "batch-1=46"),
                     rank(top(CPU, 3, Order.DES), minutes(-3, 2)));
    }

    /**
     * web-1 tops neither day, as batch-1 and backup-1 each top one, yet tops the range.
     */
    @Test
    public void keepsTheTopOfTheRangeThatTopsNoDay() throws Exception {
        assertEquals(Collections.singletonList("web-1=50"), rank(top(CPU, 1, Order.DES), minutes(-3, 2)));
    }

    /**
     * Elasticsearch's documented approximation of terms ordered by a sub-aggregation, which OAP accepts: each daily
     * index returns its own top hosts, and web-1, the top of the range, is in neither, so it is missing, and night-0's
     * mean is that of today's index alone, 62 rather than 47. The JDBC IT asserts the exact answer, web-1=50, for the
     * same hosts. A range in one index of one shard is ranked exactly, see {@link #ranksAMetricAboveTheIntRange}.
     */
    @Test
    public void ranksTheTopOfEachShardWhenNoShardHoldsTheRangesTop() throws Exception {
        assertEquals(Collections.singletonList("night-0=62"), rank(top(LOAD, 1, Order.DES), minutes(-3, 2)));
    }

    @Test
    public void ranksAMetricAboveTheIntRange() throws Exception {
        assertEquals(Arrays.asList("db-1=" + 26 * GIB, "web-1=" + 4 * GIB),
                     rank(top(MEMORY, 2, Order.DES), minutes(-2, 0)));
        assertEquals(Arrays.asList("db-1=" + 18 * GIB, "web-1=" + 5 * GIB),
                     rank(top(MEMORY, 2, Order.DES), minutes(0, 0)));
    }

    @Test
    public void ranksTheServicesEitherWayAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("resp-b=60", "resp-a=50", "resp-c=10"),
                     rank(top(RESP_TIME, 10, Order.DES), minutes(-1, 0)));
        assertEquals(Arrays.asList("resp-c=10", "resp-a=50", "resp-b=60"),
                     rank(top(RESP_TIME, 10, Order.ASC), minutes(-1, 0)));
        assertEquals(Arrays.asList("resp-b=60", "resp-a=50"), rank(top(RESP_TIME, 2, Order.DES), minutes(-1, 0)));
        assertEquals(Collections.singletonList("resp-c=10"), rank(top(RESP_TIME, 1, Order.ASC), minutes(-1, 0)));
        assertEquals(Collections.emptyList(), rank(top(RESP_TIME, 10, Order.DES), minutes(-6, -5)));
    }

    @Test
    public void ranksTheServicesOfAnAttribute() throws Exception {
        final TopNCondition general = top(RESP_TIME, 10, Order.DES);
        general.setAttributes(Collections.singletonList(new AttrCondition("attr0", "GENERAL", true)));
        assertEquals(Arrays.asList("resp-b=60", "resp-a=50"), rank(general, minutes(-1, 0)));
        final TopNCondition notGeneral = top(RESP_TIME, 10, Order.DES);
        notGeneral.setAttributes(Collections.singletonList(new AttrCondition("attr0", "GENERAL", false)));
        assertEquals(Collections.singletonList("resp-c=10"), rank(notGeneral, minutes(-1, 0)));
    }

    /**
     * The endpoints of one service, as the top-N of a service's endpoints asks with its {@code service_id}.
     */
    @Test
    public void ranksTheEndpointsOfAService() throws Exception {
        final List<KeyValue> ofService = Collections.singletonList(
            new KeyValue("service_id", IDManager.ServiceID.buildId(SERVICE, true)));
        final TopNCondition descending = top(ENDPOINT_RESP_TIME, 10, Order.DES);
        descending.setScope(Scope.Endpoint);
        assertEquals(Arrays.asList("/pay=60", "/order=50", "/cart=10"), rank(descending, minutes(-1, 0), ofService));
        final TopNCondition ascending = top(ENDPOINT_RESP_TIME, 2, Order.ASC);
        ascending.setScope(Scope.Endpoint);
        assertEquals(Arrays.asList("/cart=10", "/order=50"), rank(ascending, minutes(-1, 0), ofService));
    }

    @Test
    public void ranksAnIntMetricAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("checkout=10000", "payment=5000", "auth=2500"),
                     rank(top(APDEX, 10, Order.DES), minutes(-1, 0)));
        assertEquals(Arrays.asList("auth=2500", "payment=5000"), rank(top(APDEX, 2, Order.ASC), minutes(-1, 0)));
    }

    @Test
    public void readsTheSlowestStatementsAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList(statementRecord(4, 1500), statementRecord(1, 1200), statementRecord(3, 900)),
                     records(DATABASE, 3, Order.DES));
    }

    @Test
    public void readsTheFastestStatementsAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList(statementRecord(7, 50), statementRecord(5, 100), statementRecord(2, 300)),
                     records(DATABASE, 3, Order.ASC));
        assertEquals(Collections.emptyList(), records("idle-db:3306", 3, Order.ASC));
    }

    /**
     * Installs every model before the first write, as OAP does at boot. A column that a model installed later adds to
     * a shared index reaches its template and today's index only, so a daily index written before would map it by
     * its first value.
     */
    private void installModels() throws Exception {
        installMeter(HistogramMeter.class, LATENCY_HISTOGRAM);
        for (final String hostMetric : Arrays.asList(CPU, MEMORY, LOAD)) {
            installMeter(AvgMeter.class, hostMetric);
        }
        installMeter(LabeledMeter.class, POOL_SIZE);
        installMeter(LabeledMeter.class, POOL_WAIT);
        for (final String metric : Arrays.asList(INSTANCE_RESP_TIME, APDEX, PERCENTILE, RESP_TIME, MQ_LATENCY,
                                                 ENDPOINT_RESP_TIME, CPM, ENDPOINT_CPM)) {
            installOAL(metric, DownSampling.Minute);
        }
        installOAL(INSTANCE_RESP_TIME, DownSampling.Hour);
        models.put(key(TopNDatabaseStatement.INDEX_NAME, DownSampling.Second),
                   support.installRecord(TopNDatabaseStatement.class));
        models.put(key(TopNCacheReadCommand.INDEX_NAME, DownSampling.Second),
                   support.installRecord(TopNCacheReadCommand.class));
    }

    private void installMeter(final Class<? extends Metrics> meterClass, final String name) throws Exception {
        models.put(key(name, DownSampling.Minute),
                   support.installMeter(meterClass, DefaultScopeDefine.SERVICE, name));
    }

    private void installOAL(final String metric, final DownSampling downSampling) throws Exception {
        final Class<? extends Metrics> metricsClass = OALTestMetrics.metricsClass(metric);
        models.put(key(metric, downSampling),
                   support.installMetrics(metricsClass, downSampling));
    }

    private Model model(final String name, final DownSampling downSampling) {
        return models.get(key(name, downSampling));
    }

    private static String key(final String name, final DownSampling downSampling) {
        return name + "/" + downSampling;
    }

    private void writeHostMetric(final String name, final Object[][] hosts, final long firstMinute) throws Exception {
        final List<Metrics> metrics = new ArrayList<>();
        for (final Object[] host : hosts) {
            final long[] values = (long[]) host[1];
            for (int i = 0; i < values.length; i++) {
                if (values[i] < 0) {
                    continue;
                }
                final AvgMeter meter = new AvgMeter();
                meter.setTimeBucket(minuteBucket(firstMinute + i));
                meter.accept(MeterEntity.newService((String) host[0], Layer.OS_LINUX), values[i]);
                meter.calculate();
                metrics.add(meter);
            }
        }
        support.writeMetrics(model(name, DownSampling.Minute), new AvgFunction.AvgStorageBuilder(), metrics);
    }

    private void writeInstanceResponseTimes() throws Exception {
        final List<Metrics> minutes = new ArrayList<>();
        for (final long[] point : INSTANCE_MINUTES) {
            minutes.add(instanceResponseTime("checkout-1", minuteBucket(point[0]), point[1]));
        }
        for (long minute = -3; minute <= 2; minute++) {
            minutes.add(instanceResponseTime("checkout-2", minuteBucket(minute), 7));
        }
        writeOAL(INSTANCE_RESP_TIME, DownSampling.Minute, minutes);

        final List<Metrics> hours = new ArrayList<>();
        for (final long[] point : INSTANCE_HOURS) {
            hours.add(instanceResponseTime("checkout-1", hourBucket(point[0]), point[1]));
        }
        for (long hour = -2; hour <= 1; hour++) {
            hours.add(instanceResponseTime("checkout-2", hourBucket(hour), 7));
        }
        writeOAL(INSTANCE_RESP_TIME, DownSampling.Hour, hours);
    }

    private void writeApdex() throws Exception {
        final List<Metrics> metrics = new ArrayList<>();
        for (final Object[] service : APDEX_MINUTES) {
            final long[] values = (long[]) service[1];
            for (int i = 0; i < values.length; i++) {
                if (values[i] < 0) {
                    continue;
                }
                // An apdex of n x 2500 is n satisfied calls of four.
                final ApdexMetrics apdex = (ApdexMetrics) oal(
                    APDEX, serviceId((String) service[0]), minuteBucket(i - 1),
                    "totalNum", 4L, "sNum", values[i] / 2500, "tNum", 0L);
                apdex.calculate();
                metrics.add(apdex);
            }
        }
        writeOAL(APDEX, DownSampling.Minute, metrics);
    }

    private void writePercentiles() throws Exception {
        final List<Metrics> metrics = new ArrayList<>();
        // checkout's latencies are 10 to 1000 ms at minute -1 and twice that at minute 1.
        metrics.add(percentile(SERVICE, -1, 1));
        metrics.add(percentile(SERVICE, 1, 2));
        for (long minute = -1; minute <= 1; minute++) {
            metrics.add(percentile(OTHER_SERVICE, minute, 3));
        }
        writeOAL(PERCENTILE, DownSampling.Minute, metrics);
    }

    private void writeRankedResponseTimes() throws Exception {
        final List<Metrics> services = new ArrayList<>();
        for (final Object[] service : RANKED_SERVICES) {
            final long[] values = (long[]) service[2];
            for (int i = 0; i < values.length; i++) {
                services.add(responseTime(RESP_TIME, serviceId((String) service[0]), minuteBucket(i - 1), values[i],
                                          "attr0", service[1]));
            }
        }
        services.add(responseTime(RESP_TIME, serviceId("resp-c"), minuteBucket(-2), 1000, "attr0", "MESH"));
        services.add(responseTime(RESP_TIME, serviceId("resp-c"), minuteBucket(1), 1000, "attr0", "MESH"));
        writeOAL(RESP_TIME, DownSampling.Minute, services);

        final List<Metrics> mq = new ArrayList<>();
        for (long minute = -1; minute <= 0; minute++) {
            mq.add(responseTime(MQ_LATENCY, serviceId("resp-a"), minuteBucket(minute), 1, "attr0", "GENERAL"));
        }
        writeOAL(MQ_LATENCY, DownSampling.Minute, mq);

        final List<Metrics> endpoints = new ArrayList<>();
        for (final Object[] endpoint : RANKED_ENDPOINTS) {
            final long[] values = (long[]) endpoint[1];
            for (int i = 0; i < values.length; i++) {
                endpoints.add(responseTime(
                    ENDPOINT_RESP_TIME, endpointId(SERVICE, (String) endpoint[0]), minuteBucket(i - 1), values[i],
                    "serviceId", serviceId(SERVICE)));
            }
        }
        for (long minute = -1; minute <= 0; minute++) {
            endpoints.add(responseTime(ENDPOINT_RESP_TIME, endpointId(OTHER_SERVICE, "/order"), minuteBucket(minute),
                                       500, "serviceId", serviceId(OTHER_SERVICE)));
        }
        writeOAL(ENDPOINT_RESP_TIME, DownSampling.Minute, endpoints);
    }

    private void writeCallRates() throws Exception {
        final List<Metrics> services = new ArrayList<>();
        for (final Object[] service : CPM_MINUTES) {
            for (final long minute : (long[]) service[1]) {
                services.add(callRate(CPM, serviceId((String) service[0]), minute));
            }
        }
        writeOAL(CPM, DownSampling.Minute, services);
        writeOAL(ENDPOINT_CPM, DownSampling.Minute,
                 Collections.singletonList(callRate(ENDPOINT_CPM, endpointId(SERVICE, "/order"), 2, "serviceId",
                                                    serviceId(SERVICE))));
    }

    private void writePools() throws Exception {
        final List<Metrics> sizes = new ArrayList<>();
        for (final long[] pool : POOLS) {
            final DataTable value = new DataTable();
            value.put("{pool=active}", pool[2]);
            if (pool[3] >= 0) {
                value.put("{pool=idle}", pool[3]);
            }
            sizes.add(labeledMeter("pool-" + pool[0], pool[1], value));
        }
        support.writeMetrics(model(POOL_SIZE, DownSampling.Minute), new AvgLabeledFunction.AvgLabeledStorageBuilder(),
                             sizes);

        final List<Metrics> waits = new ArrayList<>();
        for (long minute = -1; minute <= 0; minute++) {
            for (int pool = 0; pool < WAITING_POOLS; pool++) {
                final DataTable value = new DataTable();
                final long waiters = pool * 10L + minute + 2;
                value.put("{pool=waiting}", waiters);
                waits.add(labeledMeter("pool-" + pool, minute, value));
                waiting.put(poolRowId(POOL_WAIT, pool, minute), waiters);
            }
        }
        support.writeMetrics(model(POOL_WAIT, DownSampling.Minute), new AvgLabeledFunction.AvgLabeledStorageBuilder(),
                             waits);
    }

    private void writeStatements() throws Exception {
        final List<TopNDatabaseStatement> statements = new ArrayList<>();
        for (int i = 0; i < STATEMENTS.length; i++) {
            statements.add(statement(DATABASE, i, STATEMENTS[i][0], STATEMENTS[i][1]));
        }
        for (int i = 0; i < STATEMENTS_OUTSIDE.length; i++) {
            statements.add(statement(DATABASE, 10 + i, STATEMENTS_OUTSIDE[i][0], STATEMENTS_OUTSIDE[i][1]));
        }
        statements.add(statement("inventory-db:3306", 20, 0, 9000));
        statements.add(statement("inventory-db:3306", 21, 10, 1));
        support.writeRecords(model(TopNDatabaseStatement.INDEX_NAME, DownSampling.Second),
                             new TopNDatabaseStatement.Builder(), statements);
        support.writeRecords(model(TopNCacheReadCommand.INDEX_NAME, DownSampling.Second),
                             new TopNCacheReadCommand.Builder(),
                             Arrays.asList(cacheCommand(DATABASE, 30, 5, 8000), cacheCommand(DATABASE, 31, 6, 2)));
    }

    private void writeOAL(final String metric, final DownSampling downSampling,
                          final List<Metrics> metrics) throws Exception {
        support.writeMetrics(model(metric, downSampling),
                             OALTestMetrics.builder(OALTestMetrics.metricsClass(metric)), metrics);
    }

    private List<String> values(final String metric, final Entity entity, final Duration duration) throws Exception {
        return metricsDAO.readMetricsValues(condition(metric, entity), valueColumn(metric), duration)
                      .getValues().getValues().stream()
                      .map(MetricsQueryITBase::value)
                      .collect(Collectors.toList());
    }

    /** checkout's percentiles over minutes -1 to 1, as label to values. */
    private Map<String, List<String>> labeled(final List<KeyValue> labels) throws Exception {
        final Map<String, List<String>> byLabel = new LinkedHashMap<>();
        for (final MetricsValues values : metricsDAO.readLabeledMetricsValues(
            condition(PERCENTILE, service(SERVICE)), valueColumn(PERCENTILE), labels, minutes(-1, 1))) {
            byLabel.put(values.getLabel(), values.getValues().getValues().stream()
                                                 .map(MetricsQueryITBase::value)
                                                 .collect(Collectors.toList()));
        }
        return byLabel;
    }

    /** The labeled values of every entity over minutes -1 and 0, as label to row id to value. */
    private Map<String, Map<String, String>> everyEntity(final String metric,
                                                         final List<KeyValue> labels) throws Exception {
        final Map<String, Map<String, String>> byLabel = new TreeMap<>();
        for (final MetricsValues values : metricsDAO.readLabeledMetricsValuesWithoutEntity(
            metric, valueColumn(metric), labels, minutes(-1, 0))) {
            final Map<String, String> rows = new TreeMap<>();
            for (final KVInt kv : values.getValues().getValues()) {
                rows.put(kv.getId(), value(kv));
            }
            byLabel.put(values.getLabel(), rows);
        }
        return byLabel;
    }

    /** The services listed, by name. */
    private List<String> entities(final Duration duration, final int limit) throws Exception {
        return metricsDAO.listEntityIdsInRange(CPM, valueColumn(CPM), null, duration, limit)
                      .stream()
                      .map(id -> IDManager.ServiceID.analysisId(id).getName())
                      .collect(Collectors.toList());
    }

    private List<String> rank(final TopNCondition condition, final Duration duration) throws Exception {
        return rank(condition, duration, null);
    }

    /** The ranked entities as {@code name=value}, an endpoint by its own name. */
    private List<String> rank(final TopNCondition condition, final Duration duration,
                              final List<KeyValue> additionalConditions) throws Exception {
        final List<SelectedRecord> ranked = aggregationDAO.sortMetrics(
            condition, valueColumn(condition.getName()), duration, additionalConditions);
        return ranked.stream()
                     .map(record -> (condition.getScope() == Scope.Endpoint
                         ? IDManager.EndpointID.analysisId(record.getId()).getEndpointName()
                         : IDManager.ServiceID.analysisId(record.getId()).getName()) + "=" + record.getValue())
                     .collect(Collectors.toList());
    }

    /** The records as {@code name|refId|id|value}. */
    private List<String> records(final String database, final int topN, final Order order) throws Exception {
        final Entity entity = new Entity();
        entity.setScope(Scope.Service);
        entity.setServiceName(database);
        entity.setNormal(false);
        final RecordCondition condition = new RecordCondition();
        condition.setName(TopNDatabaseStatement.INDEX_NAME);
        condition.setParentEntity(entity);
        condition.setTopN(topN);
        condition.setOrder(order);
        final List<Record> records = recordsDAO.readRecords(condition, TopN.LATENCY, minutes(-2, 1));
        return records.stream()
                      .map(record -> record.getName() + "|" + record.getRefId() + "|" + record.getId() + "|"
                          + record.getValue())
                      .collect(Collectors.toList());
    }

    private static String statementRecord(final int i, final long latency) {
        return "select * from orders where id = " + i + "|trace-" + i + "|trace-" + i + "|" + latency;
    }

    private static TopNCondition top(final String metric, final int topN, final Order order) {
        final TopNCondition condition = new TopNCondition();
        condition.setName(metric);
        condition.setScope(Scope.Service);
        condition.setTopN(topN);
        condition.setOrder(order);
        return condition;
    }

    private static String value(final KVInt kv) {
        return kv.isEmptyValue() ? "empty" : String.valueOf(kv.getValue());
    }

    private static String valueColumn(final String metric) {
        return ValueColumnMetadata.INSTANCE.getValueCName(metric);
    }

    private static Entity service(final String name) {
        final Entity entity = new Entity();
        entity.setScope(Scope.Service);
        entity.setServiceName(name);
        entity.setNormal(true);
        return entity;
    }

    private static Entity instance(final String name) {
        final Entity entity = service(SERVICE);
        entity.setScope(Scope.ServiceInstance);
        entity.setServiceInstanceName(name);
        return entity;
    }

    private static MetricsCondition condition(final String metric, final Entity entity) {
        final MetricsCondition condition = new MetricsCondition();
        condition.setName(metric);
        condition.setEntity(entity);
        return condition;
    }

    private static Duration minutes(final long first, final long last) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(MIDNIGHT + first * 60_000L));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(MIDNIGHT + last * 60_000L));
        return duration;
    }

    private static Duration hours(final long first, final long last) {
        final Duration duration = new Duration();
        duration.setStep(Step.HOUR);
        duration.setStart(DurationUtils.YYYY_MM_DD_HH.print(MIDNIGHT + first * 3_600_000L));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HH.print(MIDNIGHT + last * 3_600_000L));
        return duration;
    }

    private static long minuteBucket(final long minute) {
        return TimeBucket.getMinuteTimeBucket(MIDNIGHT + minute * 60_000L);
    }

    private static long hourBucket(final long hour) {
        return TimeBucket.getTimeBucket(MIDNIGHT + hour * 3_600_000L, DownSampling.Hour);
    }

    private static String serviceId(final String name) {
        return IDManager.ServiceID.buildId(name, true);
    }

    private static String endpointId(final String service, final String endpoint) {
        return IDManager.EndpointID.buildId(serviceId(service), endpoint);
    }

    /** The id of a pool's row: the metric, the minute and the entity, as OAP stores a MAL metric. */
    private static String poolRowId(final String metric, final long pool, final long minute) {
        return metric + "_" + minuteBucket(minute) + "_" + serviceId("pool-" + pool);
    }

    private static Metrics oal(final String metric, final String entityId, final long timeBucket,
                               final Object... properties) throws Exception {
        final Object[] all = Arrays.copyOf(properties, properties.length + 4);
        all[properties.length] = "entityId";
        all[properties.length + 1] = entityId;
        all[properties.length + 2] = "timeBucket";
        all[properties.length + 3] = timeBucket;
        return OALTestMetrics.newMetrics(OALTestMetrics.metricsClass(metric), all);
    }

    private static Metrics responseTime(final String metric, final String entityId, final long timeBucket,
                                        final long value, final Object... properties) throws Exception {
        // Two calls around the value, so the stored value is the average the function computes.
        final LongAvgMetrics metrics = (LongAvgMetrics) oal(metric, entityId, timeBucket, properties);
        metrics.combine(value - 1, 1);
        metrics.combine(value + 1, 1);
        metrics.calculate();
        return metrics;
    }

    private static Metrics instanceResponseTime(final String instance, final long timeBucket,
                                                final long value) throws Exception {
        return responseTime(INSTANCE_RESP_TIME, IDManager.ServiceInstanceID.buildId(serviceId(SERVICE), instance),
                            timeBucket, value, "serviceId", serviceId(SERVICE));
    }

    private static Metrics callRate(final String metric, final String entityId, final long minute,
                                    final Object... properties) throws Exception {
        final CPMMetrics metrics = (CPMMetrics) oal(metric, entityId, minuteBucket(minute), properties);
        metrics.combine(1);
        metrics.calculate();
        return metrics;
    }

    /** A service's percentiles of 100 calls, of {@code scale} x 10 to {@code scale} x 1000 ms. */
    private static Metrics percentile(final String service, final long minute, final int scale) throws Exception {
        final PercentileMetrics2 metrics =
            (PercentileMetrics2) oal(PERCENTILE, serviceId(service), minuteBucket(minute));
        for (int latency = 10; latency <= 1000; latency += 10) {
            metrics.combine(latency * scale, 10);
        }
        metrics.calculate();
        return metrics;
    }

    private static Metrics labeledMeter(final String service, final long minute, final DataTable value) {
        final LabeledMeter meter = new LabeledMeter();
        meter.setTimeBucket(minuteBucket(minute));
        meter.accept(MeterEntity.newService(service, Layer.GENERAL), value);
        meter.calculate();
        return meter;
    }

    private static TopNDatabaseStatement statement(final String database, final int i,
                                                   final long second, final long latency) {
        final long timestamp = MIDNIGHT + second * 1000L;
        final TopNDatabaseStatement statement = new TopNDatabaseStatement();
        statement.setId("segment-" + i + "-1");
        statement.setEntityId(IDManager.ServiceID.buildId(database, false));
        statement.setStatement("select * from orders where id = " + i);
        statement.setLatency(latency);
        statement.setTraceId("trace-" + i);
        statement.setTimestamp(timestamp);
        statement.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        return statement;
    }

    private static TopNCacheReadCommand cacheCommand(final String database, final int i,
                                                     final long second, final long latency) {
        final long timestamp = MIDNIGHT + second * 1000L;
        final TopNCacheReadCommand command = new TopNCacheReadCommand();
        command.setId("segment-" + i + "-1");
        command.setEntityId(IDManager.ServiceID.buildId(database, false));
        command.setCommand("GET order:" + i);
        command.setLatency(latency);
        command.setTraceId("trace-" + i);
        command.setTimestamp(timestamp);
        command.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        return command;
    }

    /** A MAL histogram meter: the class MAL generates adds nothing to its function but {@code createNew}. */
    static class HistogramMeter extends AvgHistogramFunction {
        @Override
        public AcceptableValue<BucketedValues> createNew() {
            return new HistogramMeter();
        }
    }

    /** A MAL meter of the default {@code avg} function. */
    static class AvgMeter extends AvgFunction {
        @Override
        public AcceptableValue<Long> createNew() {
            return new AvgMeter();
        }
    }

    /** A MAL labeled meter of the {@code avgLabeled} function. */
    static class LabeledMeter extends AvgLabeledFunction {
        @Override
        public AcceptableValue<DataTable> createNew() {
            return new LabeledMeter();
        }
    }
}
