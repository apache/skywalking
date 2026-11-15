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

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.library.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.CPMMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.AttrCondition;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Ranks metrics the way the top-N query does ({@code sortMetrics}), on the measures OAP installs for the shipped
 * {@code core.oal} metrics: the direct top-N over the raw measure (the mean per entity, ranked, with attribute
 * filters), and BanyanDB's pre-built TopN for a metric a {@code bydb-topn.yml} rule covers. Each entity's points are
 * chosen so the mean ranks them differently from the latest or the largest point: A = [10, 90], B = [60, 60],
 * C = [5, 15] rank B (60), A (50), C (10) by mean.
 */
@Testcontainers
public class BanyanDBAggregationQueryIT {
    private static final String RESP_TIME = "service_resp_time";
    private static final String ENDPOINT_CPM = "endpoint_cpm";
    private static final long[][] POINTS = {{10, 90}, {60, 60}, {5, 15}};
    private static final String SERVICE = "aggregation-it";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId(SERVICE, true);
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("aggregation-it-other", true);

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static AggregationDAO QUERY_DAO;
    private static long FIRST_MINUTE;
    private static long RECENT_MINUTE;

    @BeforeAll
    public static void writeMetrics() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final long now = System.currentTimeMillis();
        FIRST_MINUTE = (now - TimeUnit.HOURS.toMillis(1)) / 60_000L * 60_000L;
        // The pre-built TopN only takes points within its last lruSizeMinute (10) minutes.
        RECENT_MINUTE = (now - TimeUnit.MINUTES.toMillis(5)) / 60_000L * 60_000L;

        final List<Metrics> respTimes = new ArrayList<>();
        for (int entity = 0; entity < POINTS.length; entity++) {
            for (int point = 0; point < 2; point++) {
                final LongAvgMetrics metrics = (LongAvgMetrics) OALTestMetrics.newMetrics(
                    OALTestMetrics.metricsClass(RESP_TIME), "entityId", serviceId(entity),
                    "attr0", entity == 2 ? "MESH" : "GENERAL",
                    "timeBucket", TimeBucket.getMinuteTimeBucket(FIRST_MINUTE + point * 60_000L));
                metrics.combine(POINTS[entity][point], 1);
                metrics.calculate();
                respTimes.add(metrics);
            }
        }
        SUPPORT.writeMetrics(SUPPORT.installOAL(RESP_TIME, DownSampling.Minute),
                             OALTestMetrics.builder(OALTestMetrics.metricsClass(RESP_TIME)), respTimes);

        final List<Metrics> cpms = new ArrayList<>();
        for (int point = 0; point < 2; point++) {
            final long timeBucket = TimeBucket.getMinuteTimeBucket(RECENT_MINUTE + point * 60_000L);
            for (int entity = 0; entity < POINTS.length; entity++) {
                cpms.add(cpm(endpointId(SERVICE_ID, entity), SERVICE_ID, "GENERAL", POINTS[entity][point], timeBucket));
            }
            cpms.add(cpm(endpointId(OTHER_SERVICE_ID, 0), OTHER_SERVICE_ID, "MESH", 1000, timeBucket));
        }
        // The TopN stores a window whole only once a later point arrives; this one, outside the ranges read, does.
        cpms.add(cpm(endpointId(OTHER_SERVICE_ID, 9), OTHER_SERVICE_ID, "MESH", 1,
                     TimeBucket.getMinuteTimeBucket(RECENT_MINUTE + 3 * 60_000L)));
        SUPPORT.writeMetrics(SUPPORT.installOAL(ENDPOINT_CPM, DownSampling.Minute),
                             OALTestMetrics.builder(OALTestMetrics.metricsClass(ENDPOINT_CPM)), cpms);

        QUERY_DAO = new AggregationDAO(SUPPORT.getClient());
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(3, QUERY_DAO.sortMetrics(top(RESP_TIME, Scope.Service, 10, Order.DES), valueColumn(RESP_TIME),
                                                         minutes(FIRST_MINUTE), null).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void ranksByTheMeanOfEachEntity() throws IOException {
        assertEquals(Arrays.asList(service(1, 60), service(0, 50), service(2, 10)), direct(10, Order.DES, null));
        assertEquals(Arrays.asList(service(2, 10), service(0, 50), service(1, 60)), direct(10, Order.ASC, null));
    }

    @Test
    public void keepsTheTopN() throws IOException {
        assertEquals(Arrays.asList(service(1, 60), service(0, 50)), direct(2, Order.DES, null));
    }

    @Test
    public void filtersByAnAttribute() throws IOException {
        assertEquals(Arrays.asList(service(1, 60), service(0, 50)),
                     direct(10, Order.DES, new AttrCondition("attr0", "GENERAL", true)));
        assertEquals(Collections.singletonList(service(2, 10)),
                     direct(10, Order.DES, new AttrCondition("attr0", "GENERAL", false)));
    }

    @Test
    @Disabled("BanyanDB's pre-built TopN keeps one value per entity before it applies AGGREGATE BY, so MEAN over "
        + "more than one window is not the mean: apache/skywalking#14217")
    public void ranksThroughThePreBuiltTopNOfAService() throws IOException {
        final List<String> expected = Arrays.asList(endpoint(1, 60), endpoint(0, 50), endpoint(2, 10));
        final List<KeyValue> ofService = Collections.singletonList(new KeyValue("service_id", SERVICE_ID));
        final TopNCondition condition = top(ENDPOINT_CPM, Scope.Endpoint, 10, Order.DES);
        await().atMost(150, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(expected, records(QUERY_DAO.preBuilt(condition, ofService, ImmutableSet.of("service_id")))));
        assertEquals(expected, records(QUERY_DAO.sortMetrics(condition, valueColumn(ENDPOINT_CPM),
                                                             minutes(RECENT_MINUTE), ofService)));
    }

    @Test
    @Disabled("BanyanDB's pre-built TopN keeps one value per entity before it applies AGGREGATE BY, so MEAN over "
        + "more than one window is not the mean: apache/skywalking#14217")
    public void ranksThroughThePreBuiltTopNOfALayer() throws IOException {
        final TopNCondition general = top(ENDPOINT_CPM, Scope.Endpoint, 10, Order.DES);
        general.setAttributes(Collections.singletonList(new AttrCondition("attr0", "GENERAL", true)));
        await().atMost(150, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(Arrays.asList(endpoint(1, 60), endpoint(0, 50), endpoint(2, 10)),
                               records(QUERY_DAO.preBuilt(general, null, ImmutableSet.of("attr0")))));
        final TopNCondition mesh = top(ENDPOINT_CPM, Scope.Endpoint, 10, Order.DES);
        mesh.setAttributes(Collections.singletonList(new AttrCondition("attr0", "MESH", true)));
        assertEquals(Collections.singletonList(IDManager.EndpointID.buildId(OTHER_SERVICE_ID, "/endpoint-0") + "=1000"),
                     records(QUERY_DAO.sortMetrics(mesh, valueColumn(ENDPOINT_CPM), minutes(RECENT_MINUTE), null)));
    }

    @Test
    public void ranksAscendingWithoutAnAscendingRule() throws IOException {
        // The rules sort descending only, so an ascending query ranks the raw measure.
        assertEquals(Arrays.asList(endpoint(2, 10), endpoint(0, 50), endpoint(1, 60)),
                     records(QUERY_DAO.sortMetrics(top(ENDPOINT_CPM, Scope.Endpoint, 10, Order.ASC),
                                                   valueColumn(ENDPOINT_CPM), minutes(RECENT_MINUTE),
                                                   Collections.singletonList(new KeyValue("service_id", SERVICE_ID)))));
    }

    private static List<String> direct(int topN, Order order, AttrCondition attribute) throws IOException {
        final TopNCondition condition = top(RESP_TIME, Scope.Service, topN, order);
        if (attribute != null) {
            condition.setAttributes(Collections.singletonList(attribute));
        }
        return records(QUERY_DAO.sortMetrics(condition, valueColumn(RESP_TIME), minutes(FIRST_MINUTE), null));
    }

    private static List<String> records(List<SelectedRecord> records) {
        return records.stream().map(r -> r.getId() + "=" + r.getValue()).collect(Collectors.toList());
    }

    private static TopNCondition top(String metric, Scope scope, int topN, Order order) {
        final TopNCondition condition = new TopNCondition();
        condition.setName(metric);
        condition.setScope(scope);
        condition.setTopN(topN);
        condition.setOrder(order);
        return condition;
    }

    /** The two minutes from this one. */
    private static Duration minutes(long first) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(first));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(first + 60_000L));
        return duration;
    }

    private static String valueColumn(String metric) {
        return ValueColumnMetadata.INSTANCE.getValueCName(metric);
    }

    private static Metrics cpm(String endpointId, String serviceId, String layer, long calls, long timeBucket)
        throws Exception {
        final CPMMetrics metrics = (CPMMetrics) OALTestMetrics.newMetrics(
            OALTestMetrics.metricsClass(ENDPOINT_CPM), "entityId", endpointId, "serviceId", serviceId,
            "attr0", layer, "timeBucket", timeBucket);
        for (int i = 0; i < calls; i++) {
            metrics.combine(1);
        }
        metrics.calculate();
        return metrics;
    }

    private static String serviceId(int entity) {
        return IDManager.ServiceID.buildId(SERVICE + "-" + entity, true);
    }

    private static String service(int entity, long mean) {
        return serviceId(entity) + "=" + mean;
    }

    private static String endpointId(String serviceId, int entity) {
        return IDManager.EndpointID.buildId(serviceId, "/endpoint-" + entity);
    }

    private static String endpoint(int entity, long mean) {
        return endpointId(SERVICE_ID, entity) + "=" + mean;
    }

    /** Reads one TopN rule directly, so a test knows the pre-built TopN answered, not the direct fallback. */
    static class AggregationDAO extends BanyanDBAggregationQueryDAO {
        AggregationDAO(BanyanDBStorageClient client) {
            super(client);
        }

        List<SelectedRecord> preBuilt(TopNCondition condition, List<KeyValue> additionalConditions,
                                      ImmutableSet<String> ruleTags) throws IOException {
            final Duration duration = minutes(RECENT_MINUTE);
            final MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetricMetadata(
                condition.getName(), duration.getStep());
            assertNotNull(schema.getTopNSpecs().get(ruleTags), "the rule for " + ruleTags + " is installed");
            return serverSideTopN(false, condition, schema, schema.getSpec(valueColumn(condition.getName())),
                                  getTimestampRange(duration), additionalConditions,
                                  schema.getTopNSpecs().get(ruleTags).getMetadata().getName(),
                                  AbstractQuery.Sort.DESC);
        }
    }
}
