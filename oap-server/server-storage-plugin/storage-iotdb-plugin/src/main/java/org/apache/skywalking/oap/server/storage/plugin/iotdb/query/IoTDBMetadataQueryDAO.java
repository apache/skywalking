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
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;

@Slf4j
@RequiredArgsConstructor
public class IoTDBMetadataQueryDAO implements IMetadataQueryDAO {
    private final IoTDBClient client;
    private final StorageBuilder<ServiceTraffic> serviceBuilder = new ServiceTraffic.Builder();
    private final StorageBuilder<EndpointTraffic> endpointBuilder = new EndpointTraffic.Builder();
    private final StorageBuilder<InstanceTraffic> instanceBuilder = new InstanceTraffic.Builder();
    private final StorageBuilder<ProcessTraffic> processBuilder = new ProcessTraffic.Builder();

    @Override
    public List<Service> listServices(final String layer, final String group) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ServiceTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        if (StringUtil.isNotEmpty(layer)) {
            indexAndValueMap.put(IoTDBIndexes.LAYER_IDX, String.valueOf(Layer.valueOf(layer).value()));
        }
        if (StringUtil.isNotEmpty(group)) {
            indexAndValueMap.put(IoTDBIndexes.GROUP_IDX, group);
        }
        query = client.addQueryIndexValue(ServiceTraffic.INDEX_NAME, query, indexAndValueMap);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        return buildServices(storageDataList);
    }

    @Override
    public List<Service> getServices(final String serviceId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ServiceTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.SERVICE_ID_IDX, serviceId);
        query = client.addQueryIndexValue(ServiceTraffic.INDEX_NAME, query, indexAndValueMap);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        return buildServices(storageDataList);
    }

    @Override
    public List<ServiceInstance> listInstances(long startTimestamp, long endTimestamp, String serviceId) throws IOException {
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
        return buildInstances(storageDataList);
    }

    @Override
    public ServiceInstance getInstance(final String instanceId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ServiceTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.ID_IDX, instanceId);
        query = client.addQueryIndexValue(ServiceTraffic.INDEX_NAME, query, indexAndValueMap);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ServiceTraffic.INDEX_NAME, query.toString(), serviceBuilder);
        final List<ServiceInstance> instances = buildInstances(storageDataList);
        return instances.size() > 0 ? instances.get(0) : null;
    }

    @Override
    public List<Endpoint> findEndpoint(String keyword, String serviceId, int limit) throws IOException {
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
    public List<Process> listProcesses(String serviceId, String instanceId, String agentId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ProcessTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        if (StringUtil.isNotEmpty(serviceId)) {
            indexAndValueMap.put(IoTDBIndexes.SERVICE_ID_IDX, serviceId);
        }
        if (StringUtil.isNotEmpty(instanceId)) {
            indexAndValueMap.put(IoTDBIndexes.INSTANCE_ID_INX, instanceId);
        }
        if (StringUtil.isNotEmpty(agentId)) {
            indexAndValueMap.put(IoTDBIndexes.AGENT_ID_INX, agentId);
        }
        query = client.addQueryIndexValue(ProcessTraffic.INDEX_NAME, query, indexAndValueMap);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ProcessTraffic.INDEX_NAME, query.toString(), processBuilder);
        return buildProcesses(storageDataList);
    }

    @Override
    public Process getProcess(String processId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ProcessTraffic.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.ID_IDX, processId);
        query = client.addQueryIndexValue(ProcessTraffic.INDEX_NAME, query, indexAndValueMap);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ProcessTraffic.INDEX_NAME, query.toString(), processBuilder);
        final List<Process> processes = buildProcesses(storageDataList);
        return processes.size() > 0 ? processes.get(0) : null;
    }

    private List<Service> buildServices(List<? super StorageData> storageDataList) {
        List<Service> services = new ArrayList<>();
        storageDataList.forEach(storageData -> {
            ServiceTraffic serviceTraffic = (ServiceTraffic) storageData;
            String serviceName = serviceTraffic.getName();
            Service service = new Service();
            service.setId(serviceTraffic.getServiceId());
            service.setName(serviceName);
            service.setShortName(serviceTraffic.getShortName());
            service.setGroup(serviceTraffic.getGroup());
            service.getLayers().add(serviceTraffic.getLayer().name());
            services.add(service);
        });
        return services;
    }

    private List<ServiceInstance> buildInstances(List<? super StorageData> storageDataList) {
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
            serviceInstance.setLayer(instanceTraffic.getLayer().name());

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

    private List<Process> buildProcesses(List<? super StorageData> storageDataList) {
        List<Process> processes = new ArrayList<>();
        storageDataList.forEach(storageData -> {
            ProcessTraffic processTraffic = (ProcessTraffic) storageData;
            Process process = new Process();
            process.setId(processTraffic.id());
            process.setName(processTraffic.getName());
            final String serviceId = processTraffic.getServiceId();
            process.setServiceId(serviceId);
            process.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
            final String instanceId = processTraffic.getInstanceId();
            process.setInstanceId(instanceId);
            process.setInstanceName(IDManager.ServiceInstanceID.analysisId(instanceId).getName());
            process.setLayer(Layer.valueOf(processTraffic.getLayer()).name());
            process.setAgentId(processTraffic.getAgentId());
            process.setDetectType(ProcessDetectType.valueOf(processTraffic.getDetectType()).name());
            JsonObject properties = processTraffic.getProperties();
            if (properties != null) {
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    String key = property.getKey();
                    String value = property.getValue().getAsString();
                    process.getAttributes().add(new Attribute(key, value));
                }
            }
            processes.add(process);
        });
        return processes;
    }
}
