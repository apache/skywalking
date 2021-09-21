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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.base;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;
import static org.assertj.core.api.Assertions.assertThat;

public class IoTDBMetricsDAOTest {
    private IoTDBMetricsDAO metricsDAO;

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

        StorageHashMapBuilder<? extends Metrics> storageBuilder = new InstanceTraffic.Builder();
        metricsDAO = new IoTDBMetricsDAO(client, (StorageHashMapBuilder<Metrics>) storageBuilder);

        int scopeId = 1;
        boolean record = false;
        boolean superDataset = false;
        boolean timeRelativeID = true;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(InstanceTraffic.class, InstanceTraffic.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model instanceTrafficModel = new Model(
                InstanceTraffic.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(InstanceTraffic.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(instanceTrafficModel);

        StorageHashMapBuilder<InstanceTraffic> instanceTrafficBuilder = new InstanceTraffic.Builder();
        Map<String, Object> instanceTrafficMap = new HashMap<>();
        instanceTrafficMap.put(InstanceTraffic.SERVICE_ID, "service_id_1");
        instanceTrafficMap.put(InstanceTraffic.NAME, "name_1");
        instanceTrafficMap.put(InstanceTraffic.LAST_PING_TIME_BUCKET, 1L);
        instanceTrafficMap.put(InstanceTraffic.PROPERTIES, "{test: 1}");
        instanceTrafficMap.put(InstanceTraffic.ENTITY_ID, "entity_id_1");
        instanceTrafficMap.put(InstanceTraffic.TIME_BUCKET, 4L);
        InstanceTraffic instanceTraffic = instanceTrafficBuilder.storage2Entity(instanceTrafficMap);
        IoTDBInsertRequest request = new IoTDBInsertRequest(InstanceTraffic.INDEX_NAME, 4L, instanceTraffic, instanceTrafficBuilder);
        client.write(request);

        instanceTrafficMap.put(InstanceTraffic.SERVICE_ID, "service_id_2");
        instanceTrafficMap.put(InstanceTraffic.NAME, "name_2");
        instanceTrafficMap.put(InstanceTraffic.LAST_PING_TIME_BUCKET, 2L);
        instanceTrafficMap.put(InstanceTraffic.PROPERTIES, "{test: 2}");
        instanceTrafficMap.put(InstanceTraffic.ENTITY_ID, "entity_id_2");
        instanceTrafficMap.put(InstanceTraffic.TIME_BUCKET, 4L);
        instanceTraffic = instanceTrafficBuilder.storage2Entity(instanceTrafficMap);
        request = new IoTDBInsertRequest(InstanceTraffic.INDEX_NAME, 2L, instanceTraffic, instanceTrafficBuilder);
        client.write(request);
    }

    @Test
    public void multiGet() throws IOException {
        List<Metrics> metrics = new ArrayList<>();
        InstanceTraffic instanceTraffic1 = new InstanceTraffic();
        instanceTraffic1.setName("name_1");
        instanceTraffic1.setServiceId("service_id_1");
        metrics.add(instanceTraffic1);
        InstanceTraffic instanceTraffic2 = new InstanceTraffic();
        instanceTraffic2.setName("name_2");
        instanceTraffic2.setServiceId("service_id_2");
        metrics.add(instanceTraffic2);

        List<Metrics> newMetrics = metricsDAO.multiGet(IoTDBTableMetaInfo.get(InstanceTraffic.INDEX_NAME).getModel(), metrics);

        Set<String> serviceIdSet = new HashSet<>();
        serviceIdSet.add("service_id_1");
        serviceIdSet.add("service_id_2");
        Condition<String> serviceIdCondition = new Condition<>(serviceIdSet::contains, "service_id");
        Set<String> nameSet = new HashSet<>();
        nameSet.add("name_1");
        nameSet.add("name_2");
        Condition<String> nameCondition = new Condition<>(nameSet::contains, "name");

        newMetrics.forEach(metrics1 -> {
            String serviceId = ((InstanceTraffic) metrics1).getServiceId();
            String name = ((InstanceTraffic) metrics1).getName();
            assertThat(serviceId).is(serviceIdCondition);
            assertThat(name).is(nameCondition);
        });
    }
}