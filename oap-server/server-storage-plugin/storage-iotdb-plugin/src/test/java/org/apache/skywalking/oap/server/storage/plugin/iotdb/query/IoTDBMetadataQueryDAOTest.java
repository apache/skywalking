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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.type.Database;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;
import static org.assertj.core.api.Assertions.assertThat;

public class IoTDBMetadataQueryDAOTest {
    private IoTDBMetadataQueryDAO metadataQueryDAO;

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

        metadataQueryDAO = new IoTDBMetadataQueryDAO(client);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(ServiceTraffic.class, ServiceTraffic.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model serviceTrafficModel = new Model(
                ServiceTraffic.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(ServiceTraffic.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(serviceTrafficModel);

        modelColumns = new ArrayList<>();
        extraQueryIndices = new ArrayList<>();
        retrieval(EndpointTraffic.class, EndpointTraffic.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model endpointTrafficModel = new Model(
                EndpointTraffic.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(EndpointTraffic.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(endpointTrafficModel);

        scopeId = 1;
        record = false;
        superDataset = false;
        timeRelativeID = true;
        modelColumns = new ArrayList<>();
        extraQueryIndices = new ArrayList<>();
        retrieval(InstanceTraffic.class, InstanceTraffic.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model instanceTrafficModel = new Model(
                InstanceTraffic.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(InstanceTraffic.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(instanceTrafficModel);

        StorageHashMapBuilder<ServiceTraffic> serviceTrafficBuilder = new ServiceTraffic.Builder();
        Map<String, Object> serviceTrafficMap = new HashMap<>();
        serviceTrafficMap.put(ServiceTraffic.NAME, "name_1");
        serviceTrafficMap.put(ServiceTraffic.NODE_TYPE, NodeType.Normal.value());
        serviceTrafficMap.put(ServiceTraffic.GROUP, "group_1");
        serviceTrafficMap.put(ServiceTraffic.ENTITY_ID, "entity_id_1");
        serviceTrafficMap.put(ServiceTraffic.TIME_BUCKET, 1L);
        ServiceTraffic normalServiceTraffic1 = serviceTrafficBuilder.storage2Entity(serviceTrafficMap);

        IoTDBInsertRequest request = new IoTDBInsertRequest(ServiceTraffic.INDEX_NAME, 1L, normalServiceTraffic1, serviceTrafficBuilder);
        client.write(request);

        serviceTrafficMap.put(ServiceTraffic.NODE_TYPE, NodeType.Browser.value());
        ServiceTraffic browserServiceTraffic = serviceTrafficBuilder.storage2Entity(serviceTrafficMap);
        request = new IoTDBInsertRequest(ServiceTraffic.INDEX_NAME, 2L, browserServiceTraffic, serviceTrafficBuilder);
        client.write(request);

        serviceTrafficMap.put(ServiceTraffic.NODE_TYPE, NodeType.Database.value());
        ServiceTraffic databaseServiceTraffic = serviceTrafficBuilder.storage2Entity(serviceTrafficMap);
        request = new IoTDBInsertRequest(ServiceTraffic.INDEX_NAME, 3L, databaseServiceTraffic, serviceTrafficBuilder);
        client.write(request);

        serviceTrafficMap.put(ServiceTraffic.NODE_TYPE, NodeType.Normal.value());
        serviceTrafficMap.put(ServiceTraffic.NAME, "name_2");
        ServiceTraffic normalServiceTraffic2 = serviceTrafficBuilder.storage2Entity(serviceTrafficMap);
        request = new IoTDBInsertRequest(ServiceTraffic.INDEX_NAME, 4L, normalServiceTraffic2, serviceTrafficBuilder);
        client.write(request);

        StorageHashMapBuilder<EndpointTraffic> endpointTrafficBuilder = new EndpointTraffic.Builder();
        Map<String, Object> endpointTrafficMap = new HashMap<>();
        endpointTrafficMap.put(EndpointTraffic.SERVICE_ID, "service_id_1");
        endpointTrafficMap.put(EndpointTraffic.NAME, "name_1");
        endpointTrafficMap.put(EndpointTraffic.ENTITY_ID, "entity_id_1");
        endpointTrafficMap.put(EndpointTraffic.TIME_BUCKET, 1L);
        EndpointTraffic endpointTraffic = endpointTrafficBuilder.storage2Entity(endpointTrafficMap);

        request = new IoTDBInsertRequest(EndpointTraffic.INDEX_NAME, 1L, endpointTraffic, endpointTrafficBuilder);
        client.write(request);

        endpointTrafficMap.put(EndpointTraffic.NAME, "name_2");
        endpointTraffic = endpointTrafficBuilder.storage2Entity(endpointTrafficMap);
        request = new IoTDBInsertRequest(EndpointTraffic.INDEX_NAME, 2L, endpointTraffic, endpointTrafficBuilder);
        client.write(request);

        StorageHashMapBuilder<InstanceTraffic> instanceTrafficBuilder = new InstanceTraffic.Builder();
        Map<String, Object> instanceTrafficMap = new HashMap<>();
        instanceTrafficMap.put(InstanceTraffic.SERVICE_ID, "service_id_1");
        instanceTrafficMap.put(InstanceTraffic.NAME, "name_1");
        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(1L);
        instanceTrafficMap.put(InstanceTraffic.LAST_PING_TIME_BUCKET, minuteTimeBucket);
        instanceTrafficMap.put(InstanceTraffic.PROPERTIES, "{test: 1}");
        instanceTrafficMap.put(InstanceTraffic.ENTITY_ID, "entity_id_1");
        instanceTrafficMap.put(InstanceTraffic.TIME_BUCKET, 4L);
        InstanceTraffic instanceTraffic = instanceTrafficBuilder.storage2Entity(instanceTrafficMap);
        request = new IoTDBInsertRequest(InstanceTraffic.INDEX_NAME, 3L, instanceTraffic, instanceTrafficBuilder);
        client.write(request);
    }

    @Test
    public void getAllServices() throws IOException {
        List<Service> serviceList = metadataQueryDAO.getAllServices("group_1");
        serviceList.forEach(service -> {
            assertThat(service.getGroup()).isEqualTo("group_1");
        });
    }

    @Test
    public void getAllBrowserServices() throws IOException {
        List<Service> serviceList = metadataQueryDAO.getAllBrowserServices();
        serviceList.forEach(service -> {
            assertThat(service.getId()).isEqualTo(IDManager.ServiceID.buildId(service.getName(), NodeType.Browser));
        });
    }

    @Test
    public void getAllDatabases() throws IOException {
        List<Database> databaseList = metadataQueryDAO.getAllDatabases();
        databaseList.forEach(database -> {
            assertThat(database.getId()).isEqualTo(IDManager.ServiceID.buildId(database.getName(), NodeType.Database));
        });
    }

    @Test
    public void searchServices() throws IOException {
        List<Service> serviceList = metadataQueryDAO.searchServices("");

        Set<String> nameSet = new HashSet<>();
        nameSet.add("name_1");
        nameSet.add("name_2");
        Condition<String> nameCondition = new Condition<>(nameSet::contains, "name");

        serviceList.forEach(service -> {
            assertThat(service.getName()).is(nameCondition);
        });

        serviceList = metadataQueryDAO.searchServices("1");
        serviceList.forEach(service -> {
            assertThat(service.getName()).contains("1");
        });
    }

    @Test
    public void searchService() throws IOException {
        Service service = metadataQueryDAO.searchService("name_1");
        assertThat(service.getName()).contains("name_1");
    }

    @Test
    public void searchEndpoint() throws IOException {
        List<Endpoint> endpointList = metadataQueryDAO.searchEndpoint("", "service_id_1", 2);

        Set<String> nameSet = new HashSet<>();
        nameSet.add("name_1");
        nameSet.add("name_2");
        Condition<String> nameCondition = new Condition<>(nameSet::contains, "name");

        endpointList.forEach(endpoint -> {
            assertThat(endpoint.getName()).is(nameCondition);
        });

        endpointList = metadataQueryDAO.searchEndpoint("1", "service_id_1", 2);
        endpointList.forEach(endpoint -> {
            assertThat(endpoint.getName()).contains("1");
        });
    }

    @Test
    public void getServiceInstances() throws IOException {
        List<ServiceInstance> serviceInstanceList = metadataQueryDAO.getServiceInstances(0L, 10L, "service_id_1");
        serviceInstanceList.forEach(serviceInstance -> {
            assertThat(serviceInstance.getName()).isEqualTo("name_1");
        });
    }
}