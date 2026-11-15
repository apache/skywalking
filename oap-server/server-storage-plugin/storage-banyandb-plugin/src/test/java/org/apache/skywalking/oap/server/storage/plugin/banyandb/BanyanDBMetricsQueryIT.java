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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgHistogramFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgLabeledFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.PercentileMetrics2;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetricsDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetricsQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads metric values the way the metrics query service does, on the measures OAP installs for the shipped
 * {@code core.oal} metrics and for MAL meters: every point of a range longer than BanyanDB's default limit, missing
 * points as empty, the hour step, labeled values, a heat map, labeled values of every entity, and the entities seen
 * in a range newest first.
 */
@Testcontainers
public class BanyanDBMetricsQueryIT {
    private static final String RESP_TIME = "service_resp_time";
    private static final String PERCENTILE = "service_percentile";
    private static final String CPM = "service_cpm";
    private static final String HISTOGRAM = "meter_it_latency_histogram";
    private static final String LABELED = "meter_it_pool_size";
    private static final String SERVICE = "metrics-it";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId(SERVICE, true);
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("metrics-it-other", true);
    /** More minutes than BanyanDB answers without an explicit limit (100). */
    private static final int MINUTES = 130;
    private static final int GAP_MINUTE = 5;
    private static final int HOURS = 4;
    private static final int GAP_HOUR = 2;

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBMetricsQueryDAO QUERY_DAO;
    private static Model RESP_TIME_MINUTE;
    /** The first minute and hour written, aligned. */
    private static long FIRST_MINUTE;
    private static long FIRST_HOUR;
    private static final Map<Long, DataTable> PERCENTILES = new LinkedHashMap<>();
    private static final Map<Long, DataTable> HISTOGRAMS = new LinkedHashMap<>();

    @BeforeAll
    public static void writeMetrics() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        MeterEntity.setNamingControl(new NamingControl(70, 100, 150, new EndpointNameGrouping()));
        final long now = System.currentTimeMillis();
        FIRST_MINUTE = (now - TimeUnit.HOURS.toMillis(3)) / 60_000L * 60_000L;
        FIRST_HOUR = (now - TimeUnit.HOURS.toMillis(10)) / 3_600_000L * 3_600_000L;

        final Model respTimeMinute = SUPPORT.installOAL(RESP_TIME, DownSampling.Minute);
        RESP_TIME_MINUTE = respTimeMinute;
        final List<Metrics> respTimes = new ArrayList<>();
        for (int i = 0; i < MINUTES; i++) {
            if (i != GAP_MINUTE) {
                respTimes.add(respTime(SERVICE_ID, minuteBucket(i), (i + 1) * 10L));
            }
            respTimes.add(respTime(OTHER_SERVICE_ID, minuteBucket(i), 7L));
        }
        SUPPORT.writeMetrics(respTimeMinute, builder(RESP_TIME), respTimes);

        final Model respTimeHour = SUPPORT.installOAL(RESP_TIME, DownSampling.Hour);
        final List<Metrics> hourly = new ArrayList<>();
        for (int i = 0; i < HOURS; i++) {
            if (i != GAP_HOUR) {
                hourly.add(respTime(SERVICE_ID, hourBucket(i), (i + 1) * 1000L));
            }
        }
        SUPPORT.writeMetrics(respTimeHour, builder(RESP_TIME), hourly);

        final Model percentile = SUPPORT.installOAL(PERCENTILE, DownSampling.Minute);
        final List<Metrics> percentiles = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            final PercentileMetrics2 metrics = (PercentileMetrics2) OALTestMetrics.newMetrics(
                OALTestMetrics.metricsClass(PERCENTILE), "entityId", SERVICE_ID, "timeBucket", minuteBucket(i));
            for (int latency = 10; latency <= 1000; latency += 10) {
                metrics.combine(latency * (i + 1), 10);
            }
            metrics.calculate();
            PERCENTILES.put(minuteBucket(i), metrics.getPercentileValues());
            percentiles.add(metrics);
        }
        SUPPORT.writeMetrics(percentile, builder(PERCENTILE), percentiles);

        final Model histogram = SUPPORT.installMetrics(HistogramMeter.class, DefaultScopeDefine.SERVICE, HISTOGRAM);
        final List<Metrics> histograms = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            final HistogramMeter meter = new HistogramMeter();
            meter.setTimeBucket(minuteBucket(i));
            meter.accept(MeterEntity.newService(SERVICE, Layer.GENERAL),
                         new BucketedValues(new long[] {0, 50, 100}, new long[] {3 + i, 5, 1}));
            meter.calculate();
            HISTOGRAMS.put(minuteBucket(i), meter.getDataset());
            histograms.add(meter);
        }
        SUPPORT.writeMetrics(histogram, new AvgHistogramFunction.AvgHistogramFunctionBuilder(), histograms);

        final Model labeled = SUPPORT.installMetrics(LabeledMeter.class, DefaultScopeDefine.SERVICE, LABELED);
        final List<Metrics> pools = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final LabeledMeter meter = new LabeledMeter();
            meter.setTimeBucket(minuteBucket(0));
            final DataTable value = new DataTable();
            value.put("{pool=active}", 10L * (i + 1));
            value.put("{pool=idle}", 100L * (i + 1));
            meter.accept(MeterEntity.newService("pool-" + i, Layer.GENERAL), value);
            meter.calculate();
            pools.add(meter);
        }
        SUPPORT.writeMetrics(labeled, new AvgLabeledFunction.AvgLabeledStorageBuilder(), pools);

        final Model cpm = SUPPORT.installOAL(CPM, DownSampling.Minute);
        final List<Metrics> cpms = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final Metrics metrics = OALTestMetrics.newMetrics(
                OALTestMetrics.metricsClass(CPM), "entityId", IDManager.ServiceID.buildId("cpm-" + i, true),
                "timeBucket", minuteBucket(i));
            ((org.apache.skywalking.oap.server.core.analysis.metrics.CPMMetrics) metrics).combine(1);
            metrics.calculate();
            cpms.add(metrics);
        }
        SUPPORT.writeMetrics(cpm, builder(CPM), cpms);

        QUERY_DAO = new BanyanDBMetricsQueryDAO(SUPPORT.getClient());
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(3, QUERY_DAO.listEntityIdsInRange(CPM, valueColumn(CPM), null, minutes(0, 2), 10).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsEveryMinuteOfARangeLongerThanTheDefaultLimit() throws IOException {
        final List<String> expected = new ArrayList<>();
        for (int i = 0; i < MINUTES; i++) {
            expected.add(i == GAP_MINUTE ? "empty" : String.valueOf((i + 1) * 10L));
        }
        assertEquals(expected, values(QUERY_DAO.readMetricsValues(
            condition(RESP_TIME, SERVICE), valueColumn(RESP_TIME), minutes(0, MINUTES - 1))));
    }

    @Test
    public void readsTheHourStep() throws IOException {
        final Duration duration = new Duration();
        duration.setStep(Step.HOUR);
        duration.setStart(DurationUtils.YYYY_MM_DD_HH.print(FIRST_HOUR));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HH.print(FIRST_HOUR + (HOURS - 1) * 3_600_000L));
        assertEquals(Arrays.asList("1000", "2000", "empty", "4000"),
                     values(QUERY_DAO.readMetricsValues(condition(RESP_TIME, SERVICE), valueColumn(RESP_TIME), duration)));
    }

    @Test
    public void readsEveryLabelOfALabeledMetric() throws IOException {
        final Map<String, List<Long>> expected = new LinkedHashMap<>();
        for (final DataTable table : PERCENTILES.values()) {
            for (final String label : table.sortedKeys(String::compareTo)) {
                expected.computeIfAbsent(label, k -> new ArrayList<>()).add(table.get(label));
            }
        }
        assertEquals(expected, labeled(QUERY_DAO.readLabeledMetricsValues(
            condition(PERCENTILE, SERVICE), valueColumn(PERCENTILE), Collections.emptyList(), minutes(0, 1))));
    }

    @Test
    public void readsAHeatMap() throws IOException {
        final HeatMap heatMap = QUERY_DAO.readHeatMap(condition(HISTOGRAM, SERVICE), valueColumn(HISTOGRAM), minutes(0, 1));
        final List<List<Long>> columns = heatMap.getValues().stream()
            .map(column -> column.getValues().stream().map(Long::valueOf).collect(Collectors.toList()))
            .collect(Collectors.toList());
        final List<List<Long>> expected = new ArrayList<>();
        for (final DataTable dataset : HISTOGRAMS.values()) {
            expected.add(dataset.sortedKeys((a, b) -> Long.compare(Long.parseLong(a), Long.parseLong(b)))
                                .stream().map(dataset::get).collect(Collectors.toList()));
        }
        assertEquals(expected, columns);
    }

    @Test
    public void readsTheLabeledValuesOfEveryEntity() throws IOException {
        final List<MetricsValues> values = QUERY_DAO.readLabeledMetricsValuesWithoutEntity(
            LABELED, valueColumn(LABELED), Collections.emptyList(), minutes(0, 0));
        final Map<String, List<Long>> byLabel = new LinkedHashMap<>();
        for (final MetricsValues metricsValues : values) {
            byLabel.put(metricsValues.getLabel(), metricsValues.getValues().getValues().stream()
                .map(KVInt::getValue).sorted().collect(Collectors.toList()));
        }
        final Map<String, List<Long>> expected = new LinkedHashMap<>();
        expected.put("{pool=active}", Arrays.asList(10L, 20L, 30L));
        expected.put("{pool=idle}", Arrays.asList(100L, 200L, 300L));
        assertEquals(expected, byLabel);
    }

    @Test
    public void listsTheEntitiesOfARangeNewestFirst() throws IOException {
        final List<String> newestFirst = Arrays.asList(
            IDManager.ServiceID.buildId("cpm-2", true), IDManager.ServiceID.buildId("cpm-1", true),
            IDManager.ServiceID.buildId("cpm-0", true));
        assertEquals(newestFirst, QUERY_DAO.listEntityIdsInRange(CPM, valueColumn(CPM), null, minutes(0, 2), 10));
        assertEquals(newestFirst.subList(0, 2), QUERY_DAO.listEntityIdsInRange(CPM, valueColumn(CPM), null, minutes(0, 2), 2));
    }

    @Test
    public void readsBackTheMetricsAWorkerCombines() throws Exception {
        // The worker asks by id and matches what comes back by id; a missing one is simply not there.
        final BanyanDBMetricsDAO dao = new BanyanDBMetricsDAO(SUPPORT.getClient(), builder(RESP_TIME));
        final Metrics first = respTime(SERVICE_ID, minuteBucket(0), 0);
        final Metrics other = respTime(OTHER_SERVICE_ID, minuteBucket(3), 0);
        final Metrics missing = respTime(SERVICE_ID, minuteBucket(GAP_MINUTE), 0);
        final Map<String, Long> read = new LinkedHashMap<>();
        for (final Metrics metrics : dao.multiGet(RESP_TIME_MINUTE, Arrays.asList(first, other, missing))) {
            read.put(metrics.id().build(),
                     ((org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics) metrics).getValue());
        }
        assertEquals(Long.valueOf(10L), read.get(first.id().build()));
        assertEquals(Long.valueOf(7L), read.get(other.id().build()));
        assertEquals(false, read.containsKey(missing.id().build()));
    }

    /** The value column the metrics query service reads for this metric. */
    private static String valueColumn(String metric) {
        return ValueColumnMetadata.INSTANCE.getValueCName(metric);
    }

    /** The value of each point, or "empty" where the range has none. */
    private static List<String> values(MetricsValues metricsValues) {
        return metricsValues.getValues().getValues().stream()
                            .map(kv -> kv.isEmptyValue() ? "empty" : String.valueOf(kv.getValue()))
                            .collect(Collectors.toList());
    }

    private static Map<String, List<Long>> labeled(List<MetricsValues> values) {
        final Map<String, List<Long>> byLabel = new LinkedHashMap<>();
        for (final MetricsValues metricsValues : values) {
            byLabel.put(metricsValues.getLabel(), metricsValues.getValues().getValues().stream()
                .map(KVInt::getValue).collect(Collectors.toList()));
        }
        return byLabel;
    }

    private static Metrics respTime(String entityId, long timeBucket, long value) throws Exception {
        // Two calls of half the value each, so the stored value is the average the function computes.
        final Metrics metrics = OALTestMetrics.newMetrics(
            OALTestMetrics.metricsClass(RESP_TIME), "entityId", entityId, "timeBucket", timeBucket);
        final org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics avg =
            (org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics) metrics;
        avg.combine(value - 1, 1);
        avg.combine(value + 1, 1);
        avg.calculate();
        return metrics;
    }

    private static org.apache.skywalking.oap.server.core.storage.type.StorageBuilder<Metrics> builder(String name)
        throws Exception {
        return OALTestMetrics.builder(OALTestMetrics.metricsClass(name));
    }

    private static MetricsCondition condition(String metric, String service) {
        final Entity entity = new Entity();
        entity.setScope(Scope.Service);
        entity.setServiceName(service);
        entity.setNormal(true);
        final MetricsCondition condition = new MetricsCondition();
        condition.setName(metric);
        condition.setEntity(entity);
        return condition;
    }

    private static Duration minutes(int first, int last) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(FIRST_MINUTE + first * 60_000L));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(FIRST_MINUTE + last * 60_000L));
        return duration;
    }

    private static long minuteBucket(int index) {
        return TimeBucket.getMinuteTimeBucket(FIRST_MINUTE + index * 60_000L);
    }

    private static long hourBucket(int index) {
        return TimeBucket.getTimeBucket(FIRST_HOUR + index * 3_600_000L, DownSampling.Hour);
    }

    /** A MAL histogram meter: the class MAL generates adds nothing to its function but {@code createNew}. */
    public static class HistogramMeter extends AvgHistogramFunction {
        @Override
        public AcceptableValue<BucketedValues> createNew() {
            return new HistogramMeter();
        }
    }

    /** A MAL labeled meter. */
    public static class LabeledMeter extends AvgLabeledFunction {
        @Override
        public AcceptableValue<DataTable> createNew() {
            return new LabeledMeter();
        }
    }
}
