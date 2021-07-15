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
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
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
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class IoTDBMetadataQueryDAO implements IMetadataQueryDAO {
    private static final Gson GSON = new Gson();
    private final IoTDBClient client;
    private final StorageHashMapBuilder<ServiceTraffic> serviceBuilder = new ServiceTraffic.Builder();
    private final StorageHashMapBuilder<EndpointTraffic> endpointBuilder = new EndpointTraffic.Builder();
    private final StorageHashMapBuilder<InstanceTraffic> instanceBuilder = new InstanceTraffic.Builder();

    public IoTDBMetadataQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public List<Service> getAllServices(String group) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ServiceTraffic.INDEX_NAME)
                .append(" where ").append(ServiceTraffic.NODE_TYPE).append(" = ").append(NodeType.Normal.value());
        if (StringUtil.isNotEmpty(group)) {
            query.append(" and ").append(ServiceTraffic.GROUP).append(" = ").append(group);
        }
        List<Service> serviceList = new ArrayList<>();
        List<? super StorageData> storageDataList = client.queryForList(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        storageDataList.forEach(storageData -> serviceList.add(buildService((ServiceTraffic) storageData)));
        return serviceList;
    }

    @Override
    public List<Service> getAllBrowserServices() throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ServiceTraffic.INDEX_NAME)
                .append(" where ").append(ServiceTraffic.NODE_TYPE).append(" = ").append(NodeType.Browser.value());
        List<Service> serviceList = new ArrayList<>();
        List<? super StorageData> storageDataList = client.queryForList(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        storageDataList.forEach(storageData -> serviceList.add(buildService((ServiceTraffic) storageData)));
        return serviceList;
    }

    @Override
    public List<Database> getAllDatabases() throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ServiceTraffic.INDEX_NAME)
                .append(" where ").append(ServiceTraffic.NODE_TYPE).append(" = ").append(NodeType.Database.value());
        List<Database> databaseList = new ArrayList<>();
        List<? super StorageData> storageDataList = client.queryForList(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        storageDataList.forEach(storageData -> databaseList.add(buildDatabase((ServiceTraffic) storageData)));
        return databaseList;
    }

    @Override
    public List<Service> searchServices(String keyword) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select");
        if (!Strings.isNullOrEmpty(keyword)) {
            query.append(" string_contains(").append(ServiceTraffic.NAME).append(", 's'='").append(keyword).append("'),");
        }
        query.append(" * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ServiceTraffic.INDEX_NAME)
                .append(" where ").append(ServiceTraffic.NODE_TYPE).append(" = ").append(NodeType.Normal.value());

        List<? super StorageData> storageDataList;
        if (!Strings.isNullOrEmpty(keyword)) {
            storageDataList = client.queryForListWithContains(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        } else {
            storageDataList = client.queryForList(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        }
        List<Service> serviceList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> serviceList.add(buildService((ServiceTraffic) storageData)));
        return serviceList;
    }

    @Override
    public Service searchService(String serviceCode) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ServiceTraffic.INDEX_NAME)
                .append(" where ").append(ServiceTraffic.NODE_TYPE).append(" = ").append(NodeType.Normal.value());
        query.append(" and ").append(ServiceTraffic.NAME).append(" = '").append(serviceCode).append("'");
        List<? super StorageData> storageDataList = client.queryForList(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        return buildService((ServiceTraffic) storageDataList.get(0));
    }

    @Override
    public List<Endpoint> searchEndpoint(String keyword, String serviceId, int limit) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select");
        if (!Strings.isNullOrEmpty(keyword)) {
            query.append(" string_contains(").append(EndpointTraffic.NAME).append(", 's'='").append(keyword).append("'),");
        }
        query.append(" * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(EndpointTraffic.INDEX_NAME)
                .append(" where ").append(EndpointTraffic.SERVICE_ID).append(" = '").append(serviceId).append("'")
                .append(" limit ").append(limit);

        List<? super StorageData> storageDataList;
        if (!Strings.isNullOrEmpty(keyword)) {
            storageDataList = client.queryForListWithContains(EndpointTraffic.INDEX_NAME, query.toString(), endpointBuilder);
        } else {
            storageDataList = client.queryForList(EndpointTraffic.INDEX_NAME, query.toString(), endpointBuilder);
        }
        List<Endpoint> endpointList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> endpointList.add(buildEndpoint((EndpointTraffic) storageData)));
        return endpointList;
    }

    @Override
    public List<ServiceInstance> getServiceInstances(long startTimestamp, long endTimestamp, String serviceId) throws IOException {
        // TODO test and verify the structure of the table. Dose it need sub query?
        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimestamp);
        StringBuilder query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(InstanceTraffic.INDEX_NAME)
                .append(" where ").append(InstanceTraffic.LAST_PING_TIME_BUCKET).append(" >= ").append(minuteTimeBucket)
                .append(" and ").append(InstanceTraffic.SERVICE_ID).append(" = '").append(serviceId).append("'");

        List<ServiceInstance> serviceInstanceList = new ArrayList<>();
        List<? super StorageData> storageDataList = client.queryForList(InstanceTraffic.INDEX_NAME, query.toString(), instanceBuilder);
        storageDataList.forEach(storageData -> serviceInstanceList.add(buildServiceInstance((InstanceTraffic) storageData)));
        return serviceInstanceList;
    }

    private Service buildService(ServiceTraffic serviceTraffic) {
        Service service = new Service();
        service.setId(serviceTraffic.id());
        service.setName(serviceTraffic.getName());
        service.setGroup(serviceTraffic.getGroup());
        return service;
    }

    private Database buildDatabase(ServiceTraffic serviceTraffic) {
        Database database = new Database();
        database.setId(serviceTraffic.id());
        database.setName(serviceTraffic.getName());
        return database;
    }

    private Endpoint buildEndpoint(EndpointTraffic endpointTraffic) {
        Endpoint endpoint = new Endpoint();
        endpoint.setId(endpointTraffic.id());
        endpoint.setName(endpointTraffic.getName());
        return endpoint;
    }

    private ServiceInstance buildServiceInstance(InstanceTraffic instanceTraffic) {
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setId(instanceTraffic.id());
        serviceInstance.setName(instanceTraffic.getName());
        serviceInstance.setInstanceUUID(serviceInstance.getId());
        JsonObject properties = instanceTraffic.getProperties();
        // TODO test and verify the structure and content of properties
        if (!properties.isJsonNull()) {
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
        return serviceInstance;
    }
}
