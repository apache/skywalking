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
import java.util.List;
import java.util.Map;
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
import org.junit.Before;
import org.junit.Test;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;

public class IoTDBMetricsDAOTest {
    private IoTDBMetricsDAO metricsDAO;

    @Before
    public void setUp() throws Exception {
        IoTDBStorageConfig config = new IoTDBStorageConfig();
        config.setHost("127.0.0.1");
        config.setRpcPort(6667);
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
        newMetrics.forEach(metrics1 -> {
            String serviceId = ((InstanceTraffic) metrics1).getServiceId();
            String name = ((InstanceTraffic) metrics1).getName();
            assert serviceId.equals("service_id_1") || serviceId.equals("service_id_2");
            assert name.equals("name_1") || name.equals("name_2");
        });
    }
}