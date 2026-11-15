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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBTopologyQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads the relations a topology is built from, the way the topology query service does, on the measures OAP installs
 * for them. Every relation is written in two minutes, so a read must return each relation and component of the range,
 * and the topology builders key calls by id, so each read is compared as the set of calls it names. The relations are
 * chosen so a filter that matched one side of a pair alone would let a wrong relation in.
 */
@Testcontainers
public class BanyanDBTopologyQueryIT {
    private static final String A = service("a");
    private static final String B = service("b");
    private static final String C = service("c");
    private static final String D = service("d");
    private static final int HTTP = 49;
    private static final int GRPC = 23;

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBTopologyQueryDAO QUERY_DAO;
    private static long FIRST_MINUTE;

    @BeforeAll
    public static void writeRelations() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        FIRST_MINUTE = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)) / 60_000L * 60_000L;

        final List<Metrics> serverSide = new ArrayList<>();
        final List<Metrics> clientSide = new ArrayList<>();
        for (int minute = 0; minute < 2; minute++) {
            // B -> C changes component between the two minutes; each is a call of its own.
            for (final Object[] relation : new Object[][] {{A, B, HTTP}, {B, C, minute == 0 ? HTTP : GRPC}, {C, D, HTTP}}) {
                serverSide.add(serviceRelation(new ServiceRelationServerSideMetrics(), relation, minute));
                clientSide.add(serviceRelation(new ServiceRelationClientSideMetrics(), relation, minute));
            }
        }
        SUPPORT.writeMetrics(SUPPORT.installMetrics(ServiceRelationServerSideMetrics.class, DefaultScopeDefine.SERVICE_RELATION,
                                                    ServiceRelationServerSideMetrics.INDEX_NAME),
                             new ServiceRelationServerSideMetrics.Builder(), serverSide);
        SUPPORT.writeMetrics(SUPPORT.installMetrics(ServiceRelationClientSideMetrics.class, DefaultScopeDefine.SERVICE_RELATION,
                                                    ServiceRelationClientSideMetrics.INDEX_NAME),
                             new ServiceRelationClientSideMetrics.Builder(), clientSide);

        final List<Metrics> instanceServerSide = new ArrayList<>();
        final List<Metrics> instanceClientSide = new ArrayList<>();
        for (int minute = 0; minute < 2; minute++) {
            // A -> B and B -> A belong to the pair (A, B); A -> C and C -> B each match one side of it only.
            for (final String[] relation : new String[][] {{A, B}, {B, A}, {A, C}, {C, B}}) {
                instanceServerSide.add(instanceRelation(new ServiceInstanceRelationServerSideMetrics(), relation, minute));
                instanceClientSide.add(instanceRelation(new ServiceInstanceRelationClientSideMetrics(), relation, minute));
            }
        }
        SUPPORT.writeMetrics(SUPPORT.installMetrics(ServiceInstanceRelationServerSideMetrics.class,
                                                    DefaultScopeDefine.SERVICE_INSTANCE_RELATION,
                                                    ServiceInstanceRelationServerSideMetrics.INDEX_NAME),
                             new ServiceInstanceRelationServerSideMetrics.Builder(), instanceServerSide);
        SUPPORT.writeMetrics(SUPPORT.installMetrics(ServiceInstanceRelationClientSideMetrics.class,
                                                    DefaultScopeDefine.SERVICE_INSTANCE_RELATION,
                                                    ServiceInstanceRelationClientSideMetrics.INDEX_NAME),
                             new ServiceInstanceRelationClientSideMetrics.Builder(), instanceClientSide);

        final List<Metrics> endpointRelations = new ArrayList<>();
        for (int minute = 0; minute < 2; minute++) {
            for (final String[] relation : new String[][] {{"/checkout", "/pay"}, {"/pay", "/ledger"}, {"/cart", "/stock"}}) {
                endpointRelations.add(endpointRelation(relation[0], relation[1], minute));
            }
        }
        SUPPORT.writeMetrics(SUPPORT.installMetrics(EndpointRelationServerSideMetrics.class, DefaultScopeDefine.ENDPOINT_RELATION,
                                                    EndpointRelationServerSideMetrics.INDEX_NAME),
                             new EndpointRelationServerSideMetrics.Builder(), endpointRelations);

        final List<Metrics> processServerSide = new ArrayList<>();
        final List<Metrics> processClientSide = new ArrayList<>();
        for (int minute = 0; minute < 2; minute++) {
            for (final Object[] relation : new Object[][] {
                {instance(A), "p1", "p2", HTTP}, {instance(A), "p2", "p3", GRPC}, {instance(B), "p4", "p5", HTTP}}) {
                processServerSide.add(processRelation(new ProcessRelationServerSideMetrics(), relation, minute));
                processClientSide.add(processRelation(new ProcessRelationClientSideMetrics(), relation, minute));
            }
        }
        SUPPORT.writeMetrics(SUPPORT.installMetrics(ProcessRelationServerSideMetrics.class, DefaultScopeDefine.PROCESS_RELATION,
                                                    ProcessRelationServerSideMetrics.INDEX_NAME),
                             new ProcessRelationServerSideMetrics.Builder(), processServerSide);
        SUPPORT.writeMetrics(SUPPORT.installMetrics(ProcessRelationClientSideMetrics.class, DefaultScopeDefine.PROCESS_RELATION,
                                                    ProcessRelationClientSideMetrics.INDEX_NAME),
                             new ProcessRelationClientSideMetrics.Builder(), processClientSide);

        QUERY_DAO = new BanyanDBTopologyQueryDAO(SUPPORT.getClient());
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(1, calls(QUERY_DAO.loadProcessRelationDetectedAtClientSide(instance(B), range())).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsTheServiceRelationsOfSomeServices() throws IOException {
        final List<String> expected = Arrays.asList(call(A, B, HTTP), call(B, C, GRPC), call(B, C, HTTP));
        assertEquals(expected, calls(QUERY_DAO.loadServiceRelationsDetectedAtServerSide(range(), Collections.singletonList(B))));
        assertEquals(expected, calls(QUERY_DAO.loadServiceRelationDetectedAtClientSide(range(), Collections.singletonList(B))));
    }

    @Test
    public void readsEveryServiceRelation() throws IOException {
        final List<String> expected = Arrays.asList(call(A, B, HTTP), call(B, C, GRPC), call(B, C, HTTP), call(C, D, HTTP));
        assertEquals(expected, calls(QUERY_DAO.loadServiceRelationsDetectedAtServerSide(range())));
        assertEquals(expected, calls(QUERY_DAO.loadServiceRelationDetectedAtClientSide(range())));
    }

    @Test
    public void readsTheInstanceRelationsOfAServicePairBothWays() throws IOException {
        final List<String> expected = Arrays.asList(
            instanceCall(A, B), instanceCall(B, A));
        assertEquals(sorted(expected), calls(QUERY_DAO.loadInstanceRelationDetectedAtServerSide(A, B, range())));
        assertEquals(sorted(expected), calls(QUERY_DAO.loadInstanceRelationDetectedAtClientSide(A, B, range())));
    }

    @Test
    public void readsTheRelationsOfAnEndpointEitherWay() throws IOException {
        assertEquals(sorted(Arrays.asList(endpointCall("/checkout", "/pay"), endpointCall("/pay", "/ledger"))),
                     calls(QUERY_DAO.loadEndpointRelation(range(), endpoint("/pay"))));
    }

    @Test
    public void readsTheProcessRelationsOfAnInstance() throws IOException {
        final List<String> expected = sorted(Arrays.asList(
            processCall(instance(A), "p1", "p2", HTTP), processCall(instance(A), "p2", "p3", GRPC)));
        assertEquals(expected, calls(QUERY_DAO.loadProcessRelationDetectedAtServerSide(instance(A), range())));
        assertEquals(expected, calls(QUERY_DAO.loadProcessRelationDetectedAtClientSide(instance(A), range())));
    }

    /** The distinct calls read, sorted. */
    private static List<String> calls(List<Call.CallDetail> calls) {
        return calls.stream().map(c -> c.getSource() + " -> " + c.getTarget() + " #" + c.getComponentId())
                    .distinct().sorted().collect(Collectors.toList());
    }

    private static List<String> sorted(List<String> calls) {
        final List<String> sorted = new ArrayList<>(calls);
        Collections.sort(sorted);
        return sorted;
    }

    private static String call(String source, String dest, int component) {
        return source + " -> " + dest + " #" + component;
    }

    private static String instanceCall(String sourceService, String destService) {
        return instance(sourceService) + " -> " + instance(destService) + " #null";
    }

    private static String endpointCall(String source, String dest) {
        return endpoint(source) + " -> " + endpoint(dest) + " #0";
    }

    private static String processCall(String instance, String source, String dest, int component) {
        return IDManager.ProcessID.buildId(instance, source) + " -> " + IDManager.ProcessID.buildId(instance, dest)
            + " #" + component;
    }

    private static Duration range() {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(FIRST_MINUTE));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(FIRST_MINUTE + 60_000L));
        return duration;
    }

    private static long minuteBucket(int minute) {
        return TimeBucket.getMinuteTimeBucket(FIRST_MINUTE + minute * 60_000L);
    }

    private static String service(String name) {
        return IDManager.ServiceID.buildId("topology-it-" + name, true);
    }

    private static String instance(String serviceId) {
        return IDManager.ServiceInstanceID.buildId(serviceId, "instance");
    }

    private static String endpoint(String name) {
        return IDManager.EndpointID.buildId(A, name);
    }

    private static Metrics serviceRelation(Metrics metrics, Object[] relation, int minute) {
        final String source = (String) relation[0];
        final String dest = (String) relation[1];
        final IntList components = new IntList(1);
        components.add((Integer) relation[2]);
        final String entityId = IDManager.ServiceID.buildRelationId(new IDManager.ServiceID.ServiceRelationDefine(source, dest));
        if (metrics instanceof ServiceRelationServerSideMetrics) {
            final ServiceRelationServerSideMetrics m = (ServiceRelationServerSideMetrics) metrics;
            m.setSourceServiceId(source);
            m.setDestServiceId(dest);
            m.setComponentIds(components);
            m.setEntityId(entityId);
        } else {
            final ServiceRelationClientSideMetrics m = (ServiceRelationClientSideMetrics) metrics;
            m.setSourceServiceId(source);
            m.setDestServiceId(dest);
            m.setComponentIds(components);
            m.setEntityId(entityId);
        }
        metrics.setTimeBucket(minuteBucket(minute));
        return metrics;
    }

    private static Metrics instanceRelation(Metrics metrics, String[] services, int minute) {
        final String entityId = IDManager.ServiceInstanceID.buildRelationId(
            new IDManager.ServiceInstanceID.ServiceInstanceRelationDefine(instance(services[0]), instance(services[1])));
        if (metrics instanceof ServiceInstanceRelationServerSideMetrics) {
            final ServiceInstanceRelationServerSideMetrics m = (ServiceInstanceRelationServerSideMetrics) metrics;
            m.setSourceServiceId(services[0]);
            m.setSourceServiceInstanceId(instance(services[0]));
            m.setDestServiceId(services[1]);
            m.setDestServiceInstanceId(instance(services[1]));
            m.setEntityId(entityId);
        } else {
            final ServiceInstanceRelationClientSideMetrics m = (ServiceInstanceRelationClientSideMetrics) metrics;
            m.setSourceServiceId(services[0]);
            m.setSourceServiceInstanceId(instance(services[0]));
            m.setDestServiceId(services[1]);
            m.setDestServiceInstanceId(instance(services[1]));
            m.setEntityId(entityId);
        }
        metrics.setTimeBucket(minuteBucket(minute));
        return metrics;
    }

    private static Metrics endpointRelation(String source, String dest, int minute) {
        final EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
        metrics.setSourceEndpoint(endpoint(source));
        metrics.setDestEndpoint(endpoint(dest));
        metrics.setComponentId(HTTP);
        metrics.setEntityId(IDManager.EndpointID.buildRelationId(
            new IDManager.EndpointID.EndpointRelationDefine(A, source, A, dest)));
        metrics.setTimeBucket(minuteBucket(minute));
        return metrics;
    }

    private static Metrics processRelation(Metrics metrics, Object[] relation, int minute) {
        final String instance = (String) relation[0];
        final String source = IDManager.ProcessID.buildId(instance, (String) relation[1]);
        final String dest = IDManager.ProcessID.buildId(instance, (String) relation[2]);
        final String entityId = IDManager.ProcessID.buildRelationId(new IDManager.ProcessID.ProcessRelationDefine(source, dest));
        if (metrics instanceof ProcessRelationServerSideMetrics) {
            final ProcessRelationServerSideMetrics m = (ProcessRelationServerSideMetrics) metrics;
            m.setServiceInstanceId(instance);
            m.setSourceProcessId(source);
            m.setDestProcessId(dest);
            m.setComponentId((Integer) relation[3]);
            m.setEntityId(entityId);
        } else {
            final ProcessRelationClientSideMetrics m = (ProcessRelationClientSideMetrics) metrics;
            m.setServiceInstanceId(instance);
            m.setSourceProcessId(source);
            m.setDestProcessId(dest);
            m.setComponentId((Integer) relation[3]);
            m.setEntityId(entityId);
        }
        metrics.setTimeBucket(minuteBucket(minute));
        return metrics;
    }
}
