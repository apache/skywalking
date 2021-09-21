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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;

public class IoTDBMetricsQueryDAOTest {
    private IoTDBMetricsQueryDAO metricsQueryDAO;

    @Rule
    public GenericContainer iotdb = new GenericContainer(DockerImageName.parse("apache/iotdb:0.12.2-node")).withExposedPorts(6667);

    @Before
    public void setUp() throws Exception {
        IoTDBStorageConfig config = new IoTDBStorageConfig();
        config.setHost(iotdb.getHost());
        config.setRpcPort(iotdb.getFirstMappedPort());
        config.setUsername("root");
        config.setPassword("root");
        config.setStorageGroup("root.skywalking");
        config.setSessionPoolSize(3);
        config.setFetchTaskLogMaxSize(1000);

        IoTDBClient client = new IoTDBClient(config);
        client.connect();

        metricsQueryDAO = new IoTDBMetricsQueryDAO(client);

        // TODO need to find a valid test storage model, this one cannot work out.
        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(ServiceRelationServerSideMetrics.class, ServiceRelationServerSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model serviceRelationServerSideMetricsModel = new Model(
                ServiceRelationServerSideMetrics.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(ServiceRelationServerSideMetrics.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(serviceRelationServerSideMetricsModel);

        StorageHashMapBuilder<ServiceRelationServerSideMetrics> storageBuilder = new ServiceRelationServerSideMetrics.Builder();
        Map<String, Object> map = new HashMap<>();
        map.put(ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID, "service-id1");
        map.put(ServiceRelationServerSideMetrics.DEST_SERVICE_ID, "service-id2");
        map.put(ServiceRelationServerSideMetrics.COMPONENT_ID, 1);
        map.put(ServiceRelationServerSideMetrics.ENTITY_ID, "entity-id1");
        map.put(ServiceRelationServerSideMetrics.TIME_BUCKET, 1L);
        ServiceRelationServerSideMetrics serviceRelationServerSideMetrics1 = storageBuilder.storage2Entity(map);
        IoTDBInsertRequest request = new IoTDBInsertRequest(ServiceRelationServerSideMetrics.INDEX_NAME, TimeBucket.getTimestamp(10000000000001L), serviceRelationServerSideMetrics1, storageBuilder);
        client.write(request);

        map.put(ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID, "service-id2");
        map.put(ServiceRelationServerSideMetrics.DEST_SERVICE_ID, "service-id1");
        map.put(ServiceRelationServerSideMetrics.COMPONENT_ID, 2);
        map.put(ServiceRelationServerSideMetrics.ENTITY_ID, "entity-id2");
        map.put(ServiceRelationServerSideMetrics.TIME_BUCKET, 2L);
        ServiceRelationServerSideMetrics serviceRelationServerSideMetrics2 = storageBuilder.storage2Entity(map);
        request = new IoTDBInsertRequest(ServiceRelationServerSideMetrics.INDEX_NAME, TimeBucket.getTimestamp(10000000000002L), serviceRelationServerSideMetrics2, storageBuilder);
        client.write(request);
    }

    @Test
    public void readMetricsValue() throws IOException {
        // this test case cannot run
        MetricsCondition condition = new MetricsCondition();
        condition.setName(ServiceRelationServerSideMetrics.INDEX_NAME);
        Entity entity = new Entity();
        entity.setScope(Scope.ServiceRelation);
        entity.setServiceName("service_id1");
        entity.setNormal(true);
        entity.setServiceInstanceName("instance-id1");
        entity.setDestServiceName("service-id2");
        entity.setDestNormal(true);
        entity.setDestServiceInstanceName("instance-id2");
        condition.setEntity(entity);
        Duration duration = null;
//        long metricsValue = metricsQueryDAO.readMetricsValue(condition, ServiceRelationServerSideMetrics.COMPONENT_ID, duration);
//        assert metricsValue == 0;
    }

    @Test
    public void readMetricsValues() {
    }

    @Test
    public void readLabeledMetricsValues() {
    }

    @Test
    public void readHeatMap() {
    }
}