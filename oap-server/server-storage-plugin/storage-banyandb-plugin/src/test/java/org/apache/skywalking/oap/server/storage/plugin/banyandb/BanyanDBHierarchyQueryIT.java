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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBHierarchyQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads service and instance hierarchy relations the way the hierarchy query does, on the real
 * {@code service_hierarchy_relation} and {@code instance_hierarchy_relation} measures. An instance's relations are
 * found from either side of the relation, in the layer the instance is read in.
 */
@Testcontainers
public class BanyanDBHierarchyQueryIT {
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("hierarchy-it", true);
    private static final String K8S_SERVICE_ID = IDManager.ServiceID.buildId("hierarchy-it.default", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "pod-0");
    private static final String POD_ID = IDManager.ServiceInstanceID.buildId(K8S_SERVICE_ID, "pod-0");
    private static final String OTHER_INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "pod-1");
    private static final String OTHER_POD_ID = IDManager.ServiceInstanceID.buildId(K8S_SERVICE_ID, "pod-1");

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBHierarchyQueryDAO QUERY_DAO;

    @BeforeAll
    public static void writeRelations() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final long timeBucket = TimeBucket.getMinuteTimeBucket(System.currentTimeMillis());
        final ServiceHierarchyRelationTraffic services = new ServiceHierarchyRelationTraffic();
        services.setServiceId(SERVICE_ID);
        services.setServiceLayer(Layer.GENERAL);
        services.setRelatedServiceId(K8S_SERVICE_ID);
        services.setRelatedServiceLayer(Layer.K8S_SERVICE);
        services.setTimeBucket(timeBucket);
        SUPPORT.writeMetrics(
            SUPPORT.installMetrics(ServiceHierarchyRelationTraffic.class, DefaultScopeDefine.SERVICE_HIERARCHY_RELATION,
                                   ServiceHierarchyRelationTraffic.INDEX_NAME),
            new ServiceHierarchyRelationTraffic.Builder(), Collections.singletonList(services));
        SUPPORT.writeMetrics(
            SUPPORT.installMetrics(InstanceHierarchyRelationTraffic.class, DefaultScopeDefine.INSTANCE_HIERARCHY_RELATION,
                                   InstanceHierarchyRelationTraffic.INDEX_NAME),
            new InstanceHierarchyRelationTraffic.Builder(),
            Arrays.asList(instances(INSTANCE_ID, POD_ID, timeBucket), instances(OTHER_INSTANCE_ID, OTHER_POD_ID, timeBucket)));

        QUERY_DAO = new BanyanDBHierarchyQueryDAO(SUPPORT.getClient(), SUPPORT.getConfig());
        await().atMost(30, TimeUnit.SECONDS)
               .untilAsserted(() -> assertEquals(1, QUERY_DAO.readAllServiceHierarchyRelations().size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsTheServiceHierarchy() throws Exception {
        final List<String> relations = QUERY_DAO.readAllServiceHierarchyRelations().stream()
            .map(r -> r.getServiceId() + " " + r.getServiceLayer() + " -> " + r.getRelatedServiceId() + " " + r.getRelatedServiceLayer())
            .collect(Collectors.toList());
        assertEquals(Collections.singletonList(SERVICE_ID + " GENERAL -> " + K8S_SERVICE_ID + " K8S_SERVICE"), relations);
    }

    @Test
    public void readsAnInstanceHierarchyFromEitherSide() throws Exception {
        final List<String> relation = Collections.singletonList(INSTANCE_ID + " GENERAL -> " + POD_ID + " K8S_SERVICE");
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(relation, readInstance(INSTANCE_ID, Layer.GENERAL), "from the upper instance"));
        assertEquals(relation, readInstance(POD_ID, Layer.K8S_SERVICE), "from the lower instance");
        assertEquals(Collections.emptyList(), readInstance(INSTANCE_ID, Layer.K8S_SERVICE), "in a layer it is not in");
    }

    private static List<String> readInstance(String instanceId, Layer layer) throws Exception {
        return QUERY_DAO.readInstanceHierarchyRelations(instanceId, layer.name()).stream()
            .map(r -> r.getInstanceId() + " " + r.getServiceLayer() + " -> " + r.getRelatedInstanceId() + " " + r.getRelatedServiceLayer())
            .collect(Collectors.toList());
    }

    private static InstanceHierarchyRelationTraffic instances(String upper, String lower, long timeBucket) {
        final InstanceHierarchyRelationTraffic relation = new InstanceHierarchyRelationTraffic();
        relation.setInstanceId(upper);
        relation.setServiceLayer(Layer.GENERAL);
        relation.setRelatedInstanceId(lower);
        relation.setRelatedServiceLayer(Layer.K8S_SERVICE);
        relation.setTimeBucket(timeBucket);
        return relation;
    }
}
