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

import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Database;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;

@Slf4j
@RequiredArgsConstructor
public class IoTDBMetadataQueryDAO implements IMetadataQueryDAO {
    private final IoTDBClient client;
    private final StorageHashMapBuilder<ServiceTraffic> serviceBuilder = new ServiceTraffic.Builder();
    private final StorageHashMapBuilder<EndpointTraffic> endpointBuilder = new EndpointTraffic.Builder();
    private final StorageHashMapBuilder<InstanceTraffic> instanceBuilder = new InstanceTraffic.Builder();

    @Override
    public List<Service> getAllServices(String group) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ServiceTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.NODE_TYPE_IDX, String.valueOf(NodeType.Normal.value()));
        if (StringUtil.isNotEmpty(group)) {
            indexAndValueMap.put(IoTDBIndexes.GROUP_IDX, group);
        }
        query = client.addQueryIndexValue(ServiceTraffic.INDEX_NAME, query, indexAndValueMap);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        List<Service> serviceList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> serviceList.add(buildService((ServiceTraffic) storageData)));
        return serviceList;
    }

    @Override
    public List<Service> getAllBrowserServices() throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ServiceTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.NODE_TYPE_IDX, String.valueOf(NodeType.Browser.value()));
        query = client.addQueryIndexValue(ServiceTraffic.INDEX_NAME, query, indexAndValueMap);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        List<Service> serviceList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> serviceList.add(buildService((ServiceTraffic) storageData)));
        return serviceList;
    }

    @Override
    public List<Database> getAllDatabases() throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ServiceTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.NODE_TYPE_IDX, String.valueOf(NodeType.Database.value()));
        query = client.addQueryIndexValue(ServiceTraffic.INDEX_NAME, query, indexAndValueMap);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        List<Database> databaseList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> {
            ServiceTraffic serviceTraffic = (ServiceTraffic) storageData;
            Database database = new Database();
            database.setId(serviceTraffic.id());
            database.setName(serviceTraffic.getName());
            databaseList.add(database);
        });
        return databaseList;
    }

    @Override
    public List<Service> searchServices(final NodeType nodeType, final String keyword) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ServiceTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.NODE_TYPE_IDX, String.valueOf(nodeType.value()));
        query = client.addQueryIndexValue(ServiceTraffic.INDEX_NAME, query, indexAndValueMap);
        if (!Strings.isNullOrEmpty(keyword)) {
            query.append(" where ").append(ServiceTraffic.NAME).append(" like '%").append(keyword).append("%'");
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);
        List<? super StorageData> storageDataList = client.filterQuery(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        List<Service> serviceList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> serviceList.add(buildService((ServiceTraffic) storageData)));
        return serviceList;
    }

    @Override
    public Service searchService(final NodeType nodeType, final String serviceCode) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ServiceTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.NODE_TYPE_IDX, String.valueOf(nodeType.value()));
        query = client.addQueryIndexValue(ServiceTraffic.INDEX_NAME, query, indexAndValueMap);
        query.append(" where ").append(ServiceTraffic.NAME).append(" = \"").append(serviceCode).append("\"")
                .append(IoTDBClient.ALIGN_BY_DEVICE);
        List<? super StorageData> storageDataList = client.filterQuery(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        if (storageDataList.isEmpty()) {
            return null;
        }
        return buildService((ServiceTraffic) storageDataList.get(0));
    }

    @Override
    public List<Endpoint> searchEndpoint(String keyword, String serviceId, int limit) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, EndpointTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.SERVICE_ID_IDX, serviceId);
        query = client.addQueryIndexValue(EndpointTraffic.INDEX_NAME, query, indexAndValueMap);
        if (!Strings.isNullOrEmpty(keyword)) {
            query.append(" where ").append(EndpointTraffic.NAME).append(" like '%").append(keyword).append("%'");
        }
        query.append(" limit ").append(limit).append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(EndpointTraffic.INDEX_NAME, query.toString(), endpointBuilder);
        List<Endpoint> endpointList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> {
            EndpointTraffic endpointTraffic = (EndpointTraffic) storageData;
            Endpoint endpoint = new Endpoint();
            endpoint.setId(endpointTraffic.id());
            endpoint.setName(endpointTraffic.getName());
            endpointList.add(endpoint);
        });
        return endpointList;
    }

    @Override
    public List<ServiceInstance> getServiceInstances(long startTimestamp, long endTimestamp, String serviceId) throws IOException {
        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimestamp);
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, InstanceTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.SERVICE_ID_IDX, serviceId);
        query = client.addQueryIndexValue(InstanceTraffic.INDEX_NAME, query, indexAndValueMap);
        query.append(" where ").append(InstanceTraffic.LAST_PING_TIME_BUCKET).append(" >= ").append(minuteTimeBucket)
                .append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(InstanceTraffic.INDEX_NAME, query.toString(), instanceBuilder);
        List<ServiceInstance> serviceInstanceList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> {
            InstanceTraffic instanceTraffic = (InstanceTraffic) storageData;
            if (instanceTraffic.getName() == null) {
                instanceTraffic.setName("");
            }
            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setId(instanceTraffic.id());
            serviceInstance.setName(instanceTraffic.getName());
            serviceInstance.setInstanceUUID(serviceInstance.getId());

            JsonObject properties = instanceTraffic.getProperties();
            if (properties != null) {
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    String key = property.getKey();
                    String value = property.getValue().getAsString();
                    if (key.equals(InstanceTraffic.PropertyUtil.LANGUAGE)) {
                        serviceInstance.setLanguage(Language.value(value));
                    } else {
                        serviceInstance.getAttributes().add(new Attribute(key, value));
                    }
                }
            } else {
                serviceInstance.setLanguage(Language.UNKNOWN);
            }
            serviceInstanceList.add(serviceInstance);
        });
        return serviceInstanceList;
    }

    private Service buildService(ServiceTraffic serviceTraffic) {
        Service service = new Service();
        service.setId(serviceTraffic.id());
        service.setName(serviceTraffic.getName());
        service.setGroup(serviceTraffic.getGroup());
        return service;
    }
}
