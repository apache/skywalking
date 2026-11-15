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
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.otlp.OTLPServiceTraffic;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceTraffic;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBOTLPTraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBZipkinQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads the name catalogs the Zipkin and TraceQL APIs offer, on the index-mode measures OAP installs for them: every
 * service of a catalog longer than BanyanDB's default limit (a service seen in two minutes once), and the remote
 * services, peer services and span names of one service.
 */
@Testcontainers
public class BanyanDBTraceCatalogQueryIT {
    /** More services than BanyanDB returns without an explicit limit (100). */
    private static final int SERVICES = 120;

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static long MINUTE;

    @BeforeAll
    public static void writeCatalogs() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        MINUTE = TimeBucket.getMinuteTimeBucket(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
        final long nextMinute = TimeBucket.getMinuteTimeBucket(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(4));

        final List<Metrics> zipkinServices = new ArrayList<>();
        final List<Metrics> otlpServices = new ArrayList<>();
        for (int i = 0; i < SERVICES; i++) {
            final ZipkinServiceTraffic zipkin = new ZipkinServiceTraffic();
            zipkin.setServiceName(service(i));
            zipkin.setTimeBucket(MINUTE);
            zipkinServices.add(zipkin);
            final OTLPServiceTraffic otlp = new OTLPServiceTraffic();
            otlp.setServiceName(service(i));
            otlp.setLayer(i % 2 == 0 ? "GENERAL" : "MESH");
            otlp.setTimeBucket(MINUTE);
            otlpServices.add(otlp);
        }
        final ZipkinServiceTraffic again = new ZipkinServiceTraffic();
        again.setServiceName(service(0));
        again.setTimeBucket(nextMinute);
        zipkinServices.add(again);
        SUPPORT.writeMetrics(SUPPORT.installMetrics(ZipkinServiceTraffic.class, DefaultScopeDefine.ZIPKIN_SERVICE,
                                                    ZipkinServiceTraffic.INDEX_NAME), new ZipkinServiceTraffic.Builder(), zipkinServices);
        SUPPORT.writeMetrics(SUPPORT.installMetrics(OTLPServiceTraffic.class, DefaultScopeDefine.OTLP_SERVICE,
                                                    OTLPServiceTraffic.INDEX_NAME), new OTLPServiceTraffic.Builder(), otlpServices);

        SUPPORT.writeMetrics(SUPPORT.installMetrics(ZipkinServiceRelationTraffic.class, DefaultScopeDefine.ZIPKIN_SERVICE_RELATION,
                                                    ZipkinServiceRelationTraffic.INDEX_NAME),
                             new ZipkinServiceRelationTraffic.Builder(), Arrays.asList(
                zipkinRelation("frontend", "catalog"), zipkinRelation("frontend", "cart"), zipkinRelation("cart", "redis")));
        SUPPORT.writeMetrics(SUPPORT.installMetrics(ZipkinServiceSpanTraffic.class, DefaultScopeDefine.ZIPKIN_SERVICE_SPAN,
                                                    ZipkinServiceSpanTraffic.INDEX_NAME),
                             new ZipkinServiceSpanTraffic.Builder(), Arrays.asList(
                zipkinSpan("frontend", "get /"), zipkinSpan("frontend", "post /cart"), zipkinSpan("catalog", "list")));
        SUPPORT.writeMetrics(SUPPORT.installMetrics(OTLPServiceRelationTraffic.class, DefaultScopeDefine.OTLP_SERVICE_RELATION,
                                                    OTLPServiceRelationTraffic.INDEX_NAME),
                             new OTLPServiceRelationTraffic.Builder(), Arrays.asList(
                otlpRelation("frontend", "catalog"), otlpRelation("frontend", "cart"), otlpRelation("cart", "redis")));
        SUPPORT.writeMetrics(SUPPORT.installMetrics(OTLPServiceSpanTraffic.class, DefaultScopeDefine.OTLP_SERVICE_SPAN,
                                                    OTLPServiceSpanTraffic.INDEX_NAME),
                             new OTLPServiceSpanTraffic.Builder(), Arrays.asList(
                otlpSpan("frontend", "GET /"), otlpSpan("frontend", "POST /cart"), otlpSpan("catalog", "List")));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
            SERVICES, new BanyanDBZipkinQueryDAO(SUPPORT.getClient()).getServiceNames().size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsTheZipkinCatalogs() throws IOException {
        final BanyanDBZipkinQueryDAO dao = new BanyanDBZipkinQueryDAO(SUPPORT.getClient());
        assertEquals(allServices(), sorted(dao.getServiceNames()));
        assertEquals(Arrays.asList("cart", "catalog"), sorted(dao.getRemoteServiceNames("frontend")));
        assertEquals(Arrays.asList("get /", "post /cart"), sorted(dao.getSpanNames("frontend")));
        assertEquals(Collections.emptyList(), dao.getSpanNames("missing"));
    }

    @Test
    public void readsTheOTLPCatalogs() throws IOException {
        final BanyanDBOTLPTraceQueryDAO dao = new BanyanDBOTLPTraceQueryDAO(SUPPORT.getClient());
        assertEquals(allServices(), sorted(dao.getServices(null).stream()
                                              .map(OTLPServiceTraffic::getServiceName).collect(Collectors.toList())));
        final List<String> general = new ArrayList<>();
        for (int i = 0; i < SERVICES; i += 2) {
            general.add(service(i));
        }
        assertEquals(sorted(general), sorted(dao.getServices("GENERAL").stream()
                                                .map(OTLPServiceTraffic::getServiceName).collect(Collectors.toList())));
        assertEquals(Arrays.asList("cart", "catalog"), sorted(dao.getPeerServiceNames("frontend")));
        assertEquals(Arrays.asList("GET /", "POST /cart"), sorted(dao.getSpanNames("frontend")));
    }

    private static List<String> allServices() {
        final List<String> services = new ArrayList<>();
        for (int i = 0; i < SERVICES; i++) {
            services.add(service(i));
        }
        return sorted(services);
    }

    private static List<String> sorted(List<String> names) {
        return names.stream().sorted().collect(Collectors.toList());
    }

    private static String service(int i) {
        return String.format("catalog-it-%03d", i);
    }

    private static Metrics zipkinRelation(String service, String remote) {
        final ZipkinServiceRelationTraffic relation = new ZipkinServiceRelationTraffic();
        relation.setServiceName(service);
        relation.setRemoteServiceName(remote);
        relation.setTimeBucket(MINUTE);
        return relation;
    }

    private static Metrics zipkinSpan(String service, String span) {
        final ZipkinServiceSpanTraffic traffic = new ZipkinServiceSpanTraffic();
        traffic.setServiceName(service);
        traffic.setSpanName(span);
        traffic.setTimeBucket(MINUTE);
        return traffic;
    }

    private static Metrics otlpRelation(String service, String peer) {
        final OTLPServiceRelationTraffic relation = new OTLPServiceRelationTraffic();
        relation.setServiceName(service);
        relation.setPeerService(peer);
        relation.setTimeBucket(MINUTE);
        return relation;
    }

    private static Metrics otlpSpan(String service, String span) {
        final OTLPServiceSpanTraffic traffic = new OTLPServiceSpanTraffic();
        traffic.setServiceName(service);
        traffic.setSpanName(span);
        traffic.setTimeBucket(MINUTE);
        return traffic;
    }
}
