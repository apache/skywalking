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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.database.TopNDatabaseStatement;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgHistogramFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads metrics the way the metrics query service, the top-N ranking and the top-N records do, over ranges that
 * cross midnight, so over two day tables: a heat map of a MAL histogram, MAL {@code vm.yaml} metrics ranked across
 * the hosts, and the slow statements of a database. A subclass names the server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCMetricsQueryITBase {
    private static final String LATENCY_HISTOGRAM = "meter_checkout_latency_histogram";
    private static final String CPU = "meter_vm_cpu_total_percentage";
    private static final String MEMORY = "meter_vm_memory_used";
    private static final String SERVICE = "checkout";
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
    private static final long GIB = 1L << 30;
    /** Host, then the used memory in bytes of each minute from -2 to 0: db-1 averages 26 GiB, web-1 4 GiB. */
    private static final Object[][] MEMORY_MINUTES = {
        {"db-1", new long[] {30 * GIB, 30 * GIB, 18 * GIB}},
        {"web-1", new long[] {-1, 3 * GIB, 5 * GIB}},
    };
    private static final String DATABASE = "orders-db:3306";
    /** Second from midnight, latency in ms. Three before midnight, three after. */
    private static final long[][] STATEMENTS = {
        {-110, 1200},
        {-80, 300},
        {-40, 900},
        {15, 1500},
        {45, 100},
        {90, 700},
    };

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;

    JDBCMetricsQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeMetrics() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        MeterEntity.setNamingControl(new NamingControl(70, 100, 150, new EndpointNameGrouping()));

        final Model histogram = support.install(
            HistogramMeter.class, DefaultScopeDefine.SERVICE, LATENCY_HISTOGRAM, DownSampling.Minute);
        final List<Metrics> histograms = new ArrayList<>();
        for (final long[] point : HISTOGRAMS) {
            final HistogramMeter meter = new HistogramMeter();
            meter.setTimeBucket(minuteBucket(point[0]));
            meter.accept(MeterEntity.newService(SERVICE, Layer.GENERAL),
                         new BucketedValues(HISTOGRAM_BUCKETS, Arrays.copyOfRange(point, 1, point.length)));
            meter.calculate();
            histograms.add(meter);
        }
        support.writeMetrics(histogram, new AvgHistogramFunction.AvgHistogramFunctionBuilder(), histograms);

        writeHostMetric(CPU, CPU_MINUTES, -3);
        writeHostMetric(MEMORY, MEMORY_MINUTES, -2);

        final Model statementModel = support.install(
            TopNDatabaseStatement.class, DefaultScopeDefine.DATABASE_SLOW_STATEMENT, TopNDatabaseStatement.INDEX_NAME,
            DownSampling.Second);
        final List<TopNDatabaseStatement> statements = new ArrayList<>();
        for (int i = 0; i < STATEMENTS.length; i++) {
            statements.add(statement(DATABASE, "segment-" + i, STATEMENTS[i][0], STATEMENTS[i][1]));
        }
        support.writeRecords(statementModel, new TopNDatabaseStatement.Builder(), statements);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * One column per minute of the range, in minute order, each from whichever day table holds it, and an empty
     * column for the minute none holds.
     */
    @Test
    public void readsOneHeatMapColumnPerMinuteAcrossMidnight() throws Exception {
        final HeatMap heatMap = support.metricsQueryDAO().readHeatMap(
            serviceCondition(LATENCY_HISTOGRAM, SERVICE), ValueColumnMetadata.INSTANCE.getValueCName(LATENCY_HISTOGRAM),
            minutes(-2, 1));
        assertEquals(
            Arrays.asList(
                Arrays.asList(3L, 5L, 1L), Arrays.asList(0L, 0L, 0L), Arrays.asList(4L, 6L, 2L), Arrays.asList(5L, 7L, 3L)),
            heatMap.getValues().stream().map(HeatMap.HeatMapColumn::getValues).collect(Collectors.toList()));
    }

    /**
     * Each host's mean over the whole range, not the mean of its two daily means (32 for batch-1, 33 for backup-1).
     */
    @Test
    public void ranksTheMeanOfTheWholeRangeAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("web-1=50", "backup-1=47", "batch-1=46"), top(CPU, 3, minutes(-3, 2)));
    }

    /**
     * web-1 tops neither day, as batch-1 and backup-1 each top one, yet tops the range.
     */
    @Test
    public void keepsTheTopOfTheRangeThatTopsNoDay() throws Exception {
        assertEquals(Collections.singletonList("web-1=50"), top(CPU, 1, minutes(-3, 2)));
    }

    @Test
    public void ranksAMetricAboveTheIntRange() throws Exception {
        assertEquals(Arrays.asList("db-1=" + 26 * GIB, "web-1=" + 4 * GIB), top(MEMORY, 2, minutes(-2, 0)));
        // Within one day the ranking is done in SQL.
        assertEquals(Arrays.asList("db-1=" + 18 * GIB, "web-1=" + 5 * GIB), top(MEMORY, 2, minutes(0, 0)));
    }

    @Test
    public void readsTheSlowestStatementsAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("1500", "1200", "900"), latencies(DATABASE, 3, Order.DES));
    }

    @Test
    public void readsTheFastestStatementsAcrossMidnight() throws Exception {
        assertEquals(Arrays.asList("100", "300"), latencies(DATABASE, 2, Order.ASC));
    }

    private void writeHostMetric(final String name, final Object[][] hosts, final long firstMinute) throws Exception {
        final Model model = support.install(AvgMeter.class, DefaultScopeDefine.SERVICE, name, DownSampling.Minute);
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
        support.writeMetrics(model, new AvgFunction.AvgStorageBuilder(), metrics);
    }

    /** The ranked hosts as {@code host=value}. */
    private List<String> top(final String metric, final int topN, final Duration duration) throws Exception {
        final TopNCondition condition = new TopNCondition();
        condition.setName(metric);
        condition.setScope(Scope.Service);
        condition.setTopN(topN);
        condition.setOrder(Order.DES);
        final List<SelectedRecord> ranked = support.aggregationQueryDAO().sortMetrics(
            condition, ValueColumnMetadata.INSTANCE.getValueCName(metric), duration, null);
        return ranked.stream()
                     .map(record -> IDManager.ServiceID.analysisId(record.getId()).getName() + "=" + record.getValue())
                     .collect(Collectors.toList());
    }

    private List<String> latencies(final String database, final int topN, final Order order) throws Exception {
        final Entity entity = new Entity();
        entity.setScope(Scope.Service);
        entity.setServiceName(database);
        entity.setNormal(false);
        final RecordCondition condition = new RecordCondition();
        condition.setName(TopNDatabaseStatement.INDEX_NAME);
        condition.setParentEntity(entity);
        condition.setTopN(topN);
        condition.setOrder(order);
        final List<Record> records = new JDBCRecordsQueryDAO(support.getClient(), support.getTableHelper())
            .readRecords(condition, TopN.LATENCY, minutes(-2, 1));
        return records.stream().map(Record::getValue).collect(Collectors.toList());
    }

    private static MetricsCondition serviceCondition(final String metric, final String service) {
        final Entity entity = new Entity();
        entity.setScope(Scope.Service);
        entity.setServiceName(service);
        entity.setNormal(true);
        final MetricsCondition condition = new MetricsCondition();
        condition.setName(metric);
        condition.setEntity(entity);
        return condition;
    }

    private static Duration minutes(final long first, final long last) {
        return JDBCQueryITSupport.duration(MIDNIGHT + first * 60_000L, MIDNIGHT + last * 60_000L, Step.MINUTE);
    }

    private static long minuteBucket(final long minute) {
        return TimeBucket.getMinuteTimeBucket(MIDNIGHT + minute * 60_000L);
    }

    private static TopNDatabaseStatement statement(final String database, final String segmentId,
                                                   final long second, final long latency) {
        final long timestamp = MIDNIGHT + second * 1000L;
        final TopNDatabaseStatement statement = new TopNDatabaseStatement();
        statement.setId(segmentId + "-1");
        statement.setEntityId(IDManager.ServiceID.buildId(database, false));
        statement.setStatement("select * from orders where customer_id = ?");
        statement.setLatency(latency);
        statement.setTraceId("trace-" + segmentId);
        statement.setTimestamp(timestamp);
        statement.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        return statement;
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
}
