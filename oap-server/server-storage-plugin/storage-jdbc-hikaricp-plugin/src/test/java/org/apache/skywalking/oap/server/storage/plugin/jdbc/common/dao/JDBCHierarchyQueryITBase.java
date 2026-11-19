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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads service and instance hierarchy relations the way the hierarchy query service does, through
 * {@link JDBCHierarchyQueryDAO}: every service relation, and the relations of an instance in the layer it is read in,
 * found from either side of the relation. A subclass names the server.
 *
 * <p>A hierarchy relation is traffic: it stays in the day table it was first stored in, and the relations here were
 * first stored on both sides of midnight, so every read crosses two day tables. One instance id is in three layers,
 * as a service of one name is in each layer it reports to, so it is the upper instance of relations in two layers and
 * the lower instance of a relation in a third. The near misses are an instance of the same service with relations of
 * its own, and reads in a layer the instance has relations in on the other side only. One pod lies below instances in
 * twelve layers. Neither read promises an order, so the relations are compared sorted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCHierarchyQueryITBase {
    private static final int METADATA_QUERY_MAX_SIZE = 5000;
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
    private static final String EDGE_0 = instance(service("edge"), "pod-0");
    private static final String EDGE_POD_0 = instance(service("edge.default"), "pod-0");
    /** Every layer the edge service reports to, each above K8S_SERVICE. */
    private static final List<Layer> EDGE_LAYERS = Arrays.asList(
        Layer.GENERAL, Layer.MESH, Layer.MESH_DP, Layer.MYSQL, Layer.POSTGRESQL, Layer.APISIX, Layer.NGINX,
        Layer.SO11Y_OAP, Layer.ROCKETMQ, Layer.RABBITMQ, Layer.KAFKA, Layer.CLICKHOUSE);

    private static final List<String> SERVICE_RELATIONS = sorted(
        "checkout GENERAL -> checkout.default K8S_SERVICE",
        "checkout MESH -> checkout MESH_DP",
        "checkout MESH -> checkout.default K8S_SERVICE",
        "mysql::orders-db MYSQL -> orders-db.default K8S_SERVICE",
        "orders-db.default:3306 VIRTUAL_DATABASE -> mysql::orders-db MYSQL");

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCHierarchyQueryDAO dao;

    JDBCHierarchyQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeRelations() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        final long yesterday = MIDNIGHT - 2 * 3_600_000L;
        final long today = MIDNIGHT + 20 * 60_000L;

        support.writeMetrics(
            support.install(ServiceHierarchyRelationTraffic.class, DefaultScopeDefine.SERVICE_HIERARCHY_RELATION,
                            ServiceHierarchyRelationTraffic.INDEX_NAME, DownSampling.Minute),
            new ServiceHierarchyRelationTraffic.Builder(),
            Arrays.asList(
                services(CHECKOUT, Layer.GENERAL, CHECKOUT_POD, Layer.K8S_SERVICE, yesterday),
                services(CHECKOUT, Layer.MESH, CHECKOUT_POD, Layer.K8S_SERVICE, today),
                services(CHECKOUT, Layer.MESH, CHECKOUT, Layer.MESH_DP, today),
                services(MYSQL, Layer.MYSQL, MYSQL_POD, Layer.K8S_SERVICE, today),
                services(ORDERS_DB, Layer.VIRTUAL_DATABASE, MYSQL, Layer.MYSQL, yesterday)));

        final Model instanceModel = support.install(
            InstanceHierarchyRelationTraffic.class, DefaultScopeDefine.INSTANCE_HIERARCHY_RELATION,
            InstanceHierarchyRelationTraffic.INDEX_NAME, DownSampling.Minute);
        support.writeMetrics(
            instanceModel,
            new InstanceHierarchyRelationTraffic.Builder(),
            Arrays.asList(
                instances(CHECKOUT_0, Layer.GENERAL, CHECKOUT_POD_0, Layer.K8S_SERVICE, yesterday),
                instances(CHECKOUT_0, Layer.MESH, CHECKOUT_POD_0, Layer.K8S_SERVICE, today),
                instances(CHECKOUT_0, Layer.MESH, CHECKOUT_0, Layer.MESH_DP, today),
                instances(CHECKOUT_1, Layer.GENERAL, CHECKOUT_POD_1, Layer.K8S_SERVICE, today)));
        final List<InstanceHierarchyRelationTraffic> edge = new ArrayList<>();
        for (int i = 0; i < EDGE_LAYERS.size(); i++) {
            edge.add(instances(
                EDGE_0, EDGE_LAYERS.get(i), EDGE_POD_0, Layer.K8S_SERVICE, i % 2 == 0 ? yesterday : today));
        }
        support.writeMetrics(instanceModel, new InstanceHierarchyRelationTraffic.Builder(), edge);

        dao = new JDBCHierarchyQueryDAO(support.getClient(), METADATA_QUERY_MAX_SIZE, support.getTableHelper());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void readsEveryServiceRelationOfEveryDayTable() throws Exception {
        assertEquals(SERVICE_RELATIONS, serviceRelations(dao));
    }

    /**
     * The JDBC DAO reads up to twice the {@code metadataQueryMaxSize}.
     */
    @Test
    public void readsNoMoreServiceRelationsThanTheLimit() throws Exception {
        final List<String> limited = serviceRelations(
            new JDBCHierarchyQueryDAO(support.getClient(), 2, support.getTableHelper()));
        assertEquals(4, limited.size(), limited.toString());
        assertEquals(4, limited.stream().distinct().count(), limited.toString());
        assertTrue(SERVICE_RELATIONS.containsAll(limited), limited.toString());
    }

    @Test
    public void readsTheRelationsOfAnInstanceFromEitherSideInItsLayer() throws Exception {
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
    public void readsEveryRelationOfAnInstanceBelowManyLayers() throws Exception {
        assertEquals(edgeRelations(), instanceRelations(EDGE_POD_0, Layer.K8S_SERVICE));
        assertEquals(
            sorted("edge/pod-0 KAFKA -> edge.default/pod-0 K8S_SERVICE"), instanceRelations(EDGE_0, Layer.KAFKA));
    }

    @Test
    public void readsNoRelationOfAnInstanceInTheLayerOfItsOtherSide() throws Exception {
        assertEquals(Collections.emptyList(), instanceRelations(CHECKOUT_0, Layer.K8S_SERVICE));
        assertEquals(Collections.emptyList(), instanceRelations(CHECKOUT_POD_0, Layer.GENERAL));
    }

    private List<String> instanceRelations(final String instanceId, final Layer layer) throws Exception {
        return dao.readInstanceHierarchyRelations(instanceId, layer.name()).stream()
                  .map(r -> name(r.getInstanceId()) + " " + r.getServiceLayer() + " -> "
                      + name(r.getRelatedInstanceId()) + " " + r.getRelatedServiceLayer())
                  .sorted()
                  .collect(Collectors.toList());
    }

    private static List<String> serviceRelations(final JDBCHierarchyQueryDAO dao) throws Exception {
        return dao.readAllServiceHierarchyRelations().stream()
                  .map(r -> name(r.getServiceId()) + " " + r.getServiceLayer() + " -> "
                      + name(r.getRelatedServiceId()) + " " + r.getRelatedServiceLayer())
                  .sorted()
                  .collect(Collectors.toList());
    }

    private static List<String> edgeRelations() {
        return EDGE_LAYERS.stream()
                          .map(layer -> "edge/pod-0 " + layer + " -> edge.default/pod-0 K8S_SERVICE")
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
