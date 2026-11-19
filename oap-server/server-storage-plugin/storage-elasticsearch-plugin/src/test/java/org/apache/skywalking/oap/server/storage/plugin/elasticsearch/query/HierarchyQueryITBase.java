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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads service and instance hierarchy relations the way the hierarchy query service does, through
 * {@link HierarchyQueryEsDAO}: every service relation, scrolled page by page, and the relations of an instance in the
 * layer it is read in, found from either side of the relation. A nested class of a server's IT names the server and
 * the storage mode.
 *
 * <p>A hierarchy relation is traffic: it stays in the daily index it was first stored in, and the relations here
 * were first stored on both sides of midnight, so every read spans two daily indices. One instance id is in three
 * layers, as a service of one name is in each layer it reports to, so it is the upper instance of relations in two
 * layers and the lower instance of a relation in a third. The near misses are an instance of the same service with
 * relations of its own, and reads in a layer the instance has relations in on the other side only. In the default
 * mode the service and the instance relations share the merged metrics index; neither model has a routing column, so
 * the sharded mode routes them by id over both shards. Neither read promises an order, so the relations are compared
 * sorted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class HierarchyQueryITBase {
    /** A fixed local midnight, so the daily indices the relations are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    /** Fewer than the service relations, so reading them scrolls through pages. */
    private static final int SCROLLING_BATCH_SIZE = 2;
    /** Readable names of the ids the fixture builds, to print the relations read. */
    private static final Map<String, String> NAMES = new HashMap<>();

    private static final String CHECKOUT = service("checkout");
    private static final String CHECKOUT_POD = service("checkout.default");
    private static final String MYSQL = service("mysql::orders-db");
    private static final String MYSQL_POD = service("orders-db.default");
    private static final String ORDERS_DB = service("orders-db.default:3306");

    private static final String CHECKOUT_0 = instance(CHECKOUT, "pod-0");
    private static final String CHECKOUT_1 = instance(CHECKOUT, "pod-1");
    private static final String CHECKOUT_POD_0 = instance(CHECKOUT_POD, "pod-0");
    private static final String CHECKOUT_POD_1 = instance(CHECKOUT_POD, "pod-1");

    private static final List<String> SERVICE_RELATIONS = sorted(
        "checkout GENERAL -> checkout.default K8S_SERVICE",
        "checkout MESH -> checkout MESH_DP",
        "checkout MESH -> checkout.default K8S_SERVICE",
        "mysql::orders-db MYSQL -> orders-db.default K8S_SERVICE",
        "orders-db.default:3306 VIRTUAL_DATABASE -> mysql::orders-db MYSQL");

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private HierarchyQueryEsDAO dao;

    HierarchyQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeRelations() throws Exception {
        support = ElasticSearchQueryITSupport.connect(
            server, mode, getClass(), config -> config.setScrollingBatchSize(SCROLLING_BATCH_SIZE));
        // As at boot, both models are installed before anything is written: a daily index is created from the
        // index template at its first write, and maps only the columns the template had then.
        final Model serviceModel = support.installMetrics(ServiceHierarchyRelationTraffic.class);
        final Model instanceModel = support.installMetrics(InstanceHierarchyRelationTraffic.class);
        final long yesterday = MIDNIGHT - 2 * 3_600_000L;
        final long today = MIDNIGHT + 20 * 60_000L;

        support.writeMetrics(
            serviceModel,
            new ServiceHierarchyRelationTraffic.Builder(),
            Arrays.asList(
                services(CHECKOUT, Layer.GENERAL, CHECKOUT_POD, Layer.K8S_SERVICE, yesterday),
                services(CHECKOUT, Layer.MESH, CHECKOUT_POD, Layer.K8S_SERVICE, today),
                services(CHECKOUT, Layer.MESH, CHECKOUT, Layer.MESH_DP, today),
                services(MYSQL, Layer.MYSQL, MYSQL_POD, Layer.K8S_SERVICE, today),
                services(ORDERS_DB, Layer.VIRTUAL_DATABASE, MYSQL, Layer.MYSQL, yesterday)));

        support.writeMetrics(
            instanceModel,
            new InstanceHierarchyRelationTraffic.Builder(),
            Arrays.asList(
                instances(CHECKOUT_0, Layer.GENERAL, CHECKOUT_POD_0, Layer.K8S_SERVICE, yesterday),
                instances(CHECKOUT_0, Layer.MESH, CHECKOUT_POD_0, Layer.K8S_SERVICE, today),
                instances(CHECKOUT_0, Layer.MESH, CHECKOUT_0, Layer.MESH_DP, today),
                instances(CHECKOUT_1, Layer.GENERAL, CHECKOUT_POD_1, Layer.K8S_SERVICE, today)));

        dao = new HierarchyQueryEsDAO(support.getClient(), support.getConfig());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * A scroll context left open is held for the scroll's keep-alive, and a node refuses every new scroll, of any
     * query, past {@code search.max_open_scroll_context}.
     */
    @Test
    public void scrollsThroughEveryServiceRelationOfEveryDailyIndex() throws Exception {
        final long openBefore = support.openScrollContexts();
        assertEquals(SERVICE_RELATIONS, serviceRelations(dao));
        final long openAfter = support.openScrollContexts();
        assertTrue(openAfter <= openBefore,
                   "scroll contexts open before the read: " + openBefore + ", after it: " + openAfter);
    }

    /**
     * The limit falls inside the second page.
     */
    @Test
    public void readsNoMoreRelationsThanTheLimit() throws Exception {
        final StorageModuleElasticsearchConfig config = new StorageModuleElasticsearchConfig();
        config.setMetadataQueryMaxSize(3);
        config.setScrollingBatchSize(SCROLLING_BATCH_SIZE);
        final HierarchyQueryEsDAO limitedDAO = new HierarchyQueryEsDAO(support.getClient(), config);
        final long openBefore = support.openScrollContexts();
        final List<String> services = serviceRelations(limitedDAO);
        final long openAfter = support.openScrollContexts();

        assertEquals(3, services.size(), services.toString());
        assertEquals(3, services.stream().distinct().count(), services.toString());
        assertTrue(SERVICE_RELATIONS.containsAll(services), services.toString());
        assertTrue(openAfter <= openBefore,
                   "scroll contexts open before the read: " + openBefore + ", after it: " + openAfter);
    }

    @Test
    public void readsTheRelationsOfAnInstanceFromEitherSideInItsLayer() {
        assertEquals(
            sorted("checkout/pod-0 GENERAL -> checkout.default/pod-0 K8S_SERVICE"),
            instanceRelations(CHECKOUT_0, Layer.GENERAL));
        assertEquals(
            sorted(
                "checkout/pod-0 MESH -> checkout.default/pod-0 K8S_SERVICE",
                "checkout/pod-0 MESH -> checkout/pod-0 MESH_DP"),
            instanceRelations(CHECKOUT_0, Layer.MESH));
        assertEquals(
            sorted("checkout/pod-0 MESH -> checkout/pod-0 MESH_DP"),
            instanceRelations(CHECKOUT_0, Layer.MESH_DP));
        assertEquals(
            sorted(
                "checkout/pod-0 GENERAL -> checkout.default/pod-0 K8S_SERVICE",
                "checkout/pod-0 MESH -> checkout.default/pod-0 K8S_SERVICE"),
            instanceRelations(CHECKOUT_POD_0, Layer.K8S_SERVICE));
    }

    @Test
    public void readsNoRelationOfAnInstanceInTheLayerOfItsOtherSide() {
        assertEquals(Collections.emptyList(), instanceRelations(CHECKOUT_0, Layer.K8S_SERVICE));
        assertEquals(Collections.emptyList(), instanceRelations(CHECKOUT_POD_0, Layer.GENERAL));
    }

    private List<String> instanceRelations(final String instanceId, final Layer layer) {
        return instanceRelations(dao, instanceId, layer);
    }

    private static List<String> instanceRelations(final HierarchyQueryEsDAO dao, final String instanceId,
                                                  final Layer layer) {
        return dao.readInstanceHierarchyRelations(instanceId, layer.name()).stream()
                  .map(r -> name(r.getInstanceId()) + " " + r.getServiceLayer() + " -> "
                      + name(r.getRelatedInstanceId()) + " " + r.getRelatedServiceLayer())
                  .sorted()
                  .collect(Collectors.toList());
    }

    private static List<String> serviceRelations(final HierarchyQueryEsDAO dao) {
        return dao.readAllServiceHierarchyRelations().stream()
                  .map(r -> name(r.getServiceId()) + " " + r.getServiceLayer() + " -> "
                      + name(r.getRelatedServiceId()) + " " + r.getRelatedServiceLayer())
                  .sorted()
                  .collect(Collectors.toList());
    }

    private static List<String> sorted(final String... relations) {
        return Arrays.stream(relations).sorted().collect(Collectors.toList());
    }

    private static String name(final String id) {
        return NAMES.getOrDefault(id, id);
    }

    private static String service(final String name) {
        final String id = IDManager.ServiceID.buildId(name, true);
        NAMES.put(id, name);
        return id;
    }

    private static String instance(final String serviceId, final String name) {
        final String id = IDManager.ServiceInstanceID.buildId(serviceId, name);
        NAMES.put(id, NAMES.get(serviceId) + "/" + name);
        return id;
    }

    private static ServiceHierarchyRelationTraffic services(final String upper, final Layer upperLayer,
                                                            final String lower, final Layer lowerLayer,
                                                            final long firstStored) {
        final ServiceHierarchyRelationTraffic relation = new ServiceHierarchyRelationTraffic();
        relation.setServiceId(upper);
        relation.setServiceLayer(upperLayer);
        relation.setRelatedServiceId(lower);
        relation.setRelatedServiceLayer(lowerLayer);
        relation.setTimeBucket(TimeBucket.getMinuteTimeBucket(firstStored));
        return relation;
    }

    private static InstanceHierarchyRelationTraffic instances(final String upper, final Layer upperLayer,
                                                              final String lower, final Layer lowerLayer,
                                                              final long firstStored) {
        final InstanceHierarchyRelationTraffic relation = new InstanceHierarchyRelationTraffic();
        relation.setInstanceId(upper);
        relation.setServiceLayer(upperLayer);
        relation.setRelatedInstanceId(lower);
        relation.setRelatedServiceLayer(lowerLayer);
        relation.setTimeBucket(TimeBucket.getMinuteTimeBucket(firstStored));
        return relation;
    }
}
