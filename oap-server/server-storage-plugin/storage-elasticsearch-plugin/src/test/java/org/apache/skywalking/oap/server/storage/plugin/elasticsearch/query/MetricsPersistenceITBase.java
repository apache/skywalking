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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsSessionCache;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.BatchProcessEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.BatchProcessEsDAOITExt;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsDAO;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Persists metrics the way the metrics workers do, through the metrics DAO the storage DAO hands out and the batch
 * DAO, built as the storage provider builds them: a persistence round's metrics are inserted and cached once
 * Elasticsearch has answered, the next round reads the stored metrics back by id ({@code multiGet}) and writes the
 * combined ones as updates, and a cached metric expires with the day the TTL deletes ({@code isExpiredCache}). A
 * nested class of a server's IT names the server and the storage mode.
 *
 * <p>The metrics lie on both sides of midnight, so in two daily indices. Beside them, a metric of another name of
 * the same function has the same entities and minutes: OAP's default mode merges every metric into one index, and
 * logic sharding merges the metrics of one function. A metadata metric, whose id has no time bucket, is stored
 * before midnight and read with a later minute, as the metrics worker asks for it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class MetricsPersistenceITBase {
    /** A fixed local midnight, so the daily indices the metrics are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    private static final String RESP_TIME = "service_resp_time";
    private static final String OTHER_RESP_TIME = "service_relation_server_resp_time";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("payment", true);
    private static final int[] MINUTES = {-1, 0, 1};
    private static final int METRICS_TTL_DAYS = 7;

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private BatchProcessEsDAO batchDAO;
    private StorageDAO storageDAO;
    private Model respTime;
    private Model traffic;
    private StorageBuilder<Metrics> respTimeBuilder;

    MetricsPersistenceITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void install() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        final Class<? extends Metrics> respTimeClass = OALTestMetrics.metricsClass(RESP_TIME);
        final Class<? extends Metrics> otherRespTimeClass = OALTestMetrics.metricsClass(OTHER_RESP_TIME);
        respTime = support.installMetrics(respTimeClass);
        final Model otherRespTime = support.installMetrics(otherRespTimeClass);
        traffic = support.installMetrics(ServiceTraffic.class);
        respTimeBuilder = OALTestMetrics.builder(respTimeClass);

        final List<Metrics> sameEntities = new ArrayList<>();
        for (final int minute : MINUTES) {
            for (final String entityId : Arrays.asList(SERVICE_ID, OTHER_SERVICE_ID)) {
                sameEntities.add(respTime(OTHER_RESP_TIME, entityId, minute, 7L));
            }
        }
        support.writeMetrics(otherRespTime, OALTestMetrics.builder(otherRespTimeClass), sameEntities);

        final StorageModuleElasticsearchConfig config = support.getConfig();
        batchDAO = new BatchProcessEsDAO(
            support.getClient(), config.getBulkActions(), config.getFlushInterval(), config.getConcurrentRequests(),
            config.getBatchOfBytes());
        storageDAO = new StorageEsDAO(support.getClient());
    }

    @AfterAll
    public void close() {
        if (batchDAO != null) {
            BatchProcessEsDAOITExt.close(batchDAO);
        }
        if (support != null) {
            support.close();
        }
    }

    /**
     * The metrics worker caches each metric of a round once its insert is answered, reads back by id the metrics it
     * does not hold, and writes a metric combined with what came in since as an update of the stored one.
     */
    @Test
    public void writesTheMetricsOfAPersistenceRoundAndReadsThemBackById() throws Exception {
        final IMetricsDAO dao = storageDAO.newMetricsDao(respTimeBuilder);
        final MetricsSessionCache sessionCache = new MetricsSessionCache(TimeUnit.MINUTES.toMillis(1), true);
        final List<Metrics> inserted = new ArrayList<>();
        final List<PrepareRequest> round = new ArrayList<>();
        for (final int minute : MINUTES) {
            for (final String entityId : Arrays.asList(SERVICE_ID, OTHER_SERVICE_ID)) {
                final Metrics metrics = respTime(RESP_TIME, entityId, minute, 100L + minute);
                inserted.add(metrics);
                round.add(dao.prepareBatchInsert(respTime, metrics, new SessionCacheCallback(sessionCache, metrics)));
            }
        }
        persistenceRound(round);

        assertEquals(columns(inserted), columns(inserted.stream().map(sessionCache::remove)
                                                        .collect(Collectors.toList())),
                     "every metric of the round is cached once Elasticsearch has answered");
        final List<Metrics> asked = new ArrayList<>(inserted);
        asked.add(respTime(RESP_TIME, IDManager.ServiceID.buildId("never-written", true), 0, 1L));
        assertEquals(columns(inserted), columns(dao.multiGet(respTime, asked)));

        final Metrics combined = respTime(RESP_TIME, SERVICE_ID, 0, 100L, 300L);
        persistenceRound(Collections.singletonList(dao.prepareBatchUpdate(respTime, combined, null)));
        final List<Metrics> afterUpdate = inserted.stream()
            .map(metrics -> metrics.id().build().equals(combined.id().build()) ? combined : metrics)
            .collect(Collectors.toList());
        assertEquals(columns(afterUpdate), columns(dao.multiGet(respTime, inserted)));
    }

    /**
     * A metadata metric has one row for all time. The metrics worker asks for it with the minute it sees it in again,
     * not the minute it was stored with.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void readsAMetadataMetricBackWhicheverDayItIsStoredIn() throws Exception {
        final StorageBuilder<Metrics> builder = (StorageBuilder) new ServiceTraffic.Builder();
        final ServiceTraffic stored = service("checkout", Layer.GENERAL, -1);
        support.writeMetrics(traffic, builder, Arrays.asList(stored, service("checkout", Layer.MESH, -1)));

        final List<Metrics> read = storageDAO.newMetricsDao(builder).multiGet(traffic, Arrays.asList(
            service("checkout", Layer.GENERAL, 1), service("never-seen", Layer.GENERAL, 1)));

        assertEquals(columns(builder, Collections.<Metrics>singletonList(stored)), columns(builder, read));
    }

    /**
     * The TTL deletes the daily indices up to the local calendar day {@code ttl} days before today, so a metric of
     * that day is expired however late in the day it is, and a metric of the next day is not, however early.
     */
    @Test
    public void expiresAMetricWithTheDayTheTTLDeletes() throws Exception {
        final IMetricsDAO dao = storageDAO.newMetricsDao(respTimeBuilder);
        final DateTime now = new DateTime(MIDNIGHT).withHourOfDay(10);
        final LocalDate lastDeletedDay = now.toLocalDate().minusDays(METRICS_TTL_DAYS);
        final long firstKeptDayStart = lastDeletedDay.plusDays(1).toDateTimeAtStartOfDay().getMillis();
        assertEquals(
            Arrays.asList(true, true, false, false),
            Arrays.asList(
                expired(dao, lastDeletedDay.toDateTimeAtStartOfDay().getMillis(), now.getMillis()),
                expired(dao, firstKeptDayStart - TimeUnit.MINUTES.toMillis(1), now.getMillis()),
                expired(dao, firstKeptDayStart, now.getMillis()),
                expired(dao, now.getMillis(), now.getMillis())));
    }

    private boolean expired(final IMetricsDAO dao, final long metricTime, final long now) throws Exception {
        final Metrics metrics = OALTestMetrics.newMetrics(
            OALTestMetrics.metricsClass(RESP_TIME), "entityId", SERVICE_ID,
            "timeBucket", TimeBucket.getMinuteTimeBucket(metricTime));
        return dao.isExpiredCache(respTime, metrics, now, METRICS_TTL_DAYS);
    }

    /**
     * One worker's share of a persistence round, as the persistence timer writes it: the flush, the end of the round,
     * and the wait for the answer.
     */
    private void persistenceRound(final List<PrepareRequest> requests) throws Exception {
        final CompletableFuture<Void> answered = batchDAO.flush(requests);
        batchDAO.endOfFlush();
        answered.get(30, TimeUnit.SECONDS);
        support.refresh();
    }

    private List<String> columns(final List<Metrics> metrics) {
        return columns(respTimeBuilder, metrics);
    }

    /**
     * Every column of each metric, as the metrics workers store and read them, in no order.
     */
    private static List<String> columns(final StorageBuilder<Metrics> builder, final List<Metrics> metrics) {
        return metrics.stream().map(metric -> {
            final HashMapConverter.ToStorage columns = new HashMapConverter.ToStorage();
            builder.entity2Storage(metric, columns);
            return new TreeMap<>(columns.obtain()).toString();
        }).sorted().collect(Collectors.toList());
    }

    /**
     * The response time of a minute of an entity, one call of each latency.
     */
    private static Metrics respTime(final String metric, final String entityId, final int minute,
                                    final long... latencies) throws Exception {
        final LongAvgMetrics metrics = (LongAvgMetrics) OALTestMetrics.newMetrics(
            OALTestMetrics.metricsClass(metric), "entityId", entityId,
            "timeBucket", TimeBucket.getMinuteTimeBucket(MIDNIGHT + TimeUnit.MINUTES.toMillis(minute)));
        for (final long latency : latencies) {
            metrics.combine(latency, 1);
        }
        metrics.calculate();
        return metrics;
    }

    private static ServiceTraffic service(final String name, final Layer layer, final int minute) {
        final ServiceTraffic service = new ServiceTraffic();
        service.setName(name);
        service.setLayer(layer);
        service.setTimeBucket(TimeBucket.getMinuteTimeBucket(MIDNIGHT + TimeUnit.MINUTES.toMillis(minute)));
        return service;
    }
}
