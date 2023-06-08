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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic.PropertyUtil.LANGUAGE;

public class BanyanDBMetadataQueryDAO extends AbstractBanyanDBDAO implements IMetadataQueryDAO {
    private static final Set<String> SERVICE_TRAFFIC_TAGS = ImmutableSet.of(ServiceTraffic.NAME,
            ServiceTraffic.SHORT_NAME, ServiceTraffic.GROUP, ServiceTraffic.LAYER, ServiceTraffic.SERVICE_ID);

    private static final Set<String> INSTANCE_TRAFFIC_TAGS = ImmutableSet.of(InstanceTraffic.NAME,
            InstanceTraffic.PROPERTIES, InstanceTraffic.LAST_PING_TIME_BUCKET, InstanceTraffic.SERVICE_ID);

    private static final Set<String> INSTANCE_TRAFFIC_COMPACT_TAGS = ImmutableSet.of(InstanceTraffic.NAME,
            InstanceTraffic.PROPERTIES);

    private static final Set<String> ENDPOINT_TRAFFIC_TAGS = ImmutableSet.of(EndpointTraffic.NAME,
            EndpointTraffic.SERVICE_ID);

    private static final Set<String> PROCESS_TRAFFIC_TAGS = ImmutableSet.of(ProcessTraffic.NAME,
            ProcessTraffic.SERVICE_ID, ProcessTraffic.INSTANCE_ID, ProcessTraffic.AGENT_ID, ProcessTraffic.DETECT_TYPE,
            ProcessTraffic.PROPERTIES, ProcessTraffic.LABELS_JSON, ProcessTraffic.LAST_PING_TIME_BUCKET,
            ProcessTraffic.PROFILING_SUPPORT_STATUS);

    private static final Set<String> PROCESS_TRAFFIC_COMPACT_TAGS = ImmutableSet.of(ProcessTraffic.NAME,
            ProcessTraffic.SERVICE_ID, ProcessTraffic.INSTANCE_ID, ProcessTraffic.AGENT_ID, ProcessTraffic.DETECT_TYPE,
            ProcessTraffic.PROPERTIES, ProcessTraffic.LABELS_JSON);

    private static final Gson GSON = new Gson();

    public BanyanDBMetadataQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<Service> listServices(String layer, String group) throws IOException {
        MeasureQueryResponse resp = query(ServiceTraffic.INDEX_NAME,
                SERVICE_TRAFFIC_TAGS,
                Collections.emptySet(), new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        if (StringUtil.isNotEmpty(group)) {
                            query.and(eq(ServiceTraffic.GROUP, group));
                        }
                        if (StringUtil.isNotEmpty(layer)) {
                            query.and(eq(ServiceTraffic.LAYER, Layer.valueOf(layer).value()));
                        }
                    }
                });

        final List<Service> services = new ArrayList<>();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(ServiceTraffic.INDEX_NAME, DownSampling.Minute);

        for (final DataPoint dataPoint : resp.getDataPoints()) {
            services.add(buildService(dataPoint, schema));
        }

        return services;
    }

    @Override
    public List<Service> getServices(String serviceId) throws IOException {
        MeasureQueryResponse resp = query(ServiceTraffic.INDEX_NAME,
                SERVICE_TRAFFIC_TAGS,
                Collections.emptySet(), new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        if (StringUtil.isNotEmpty(serviceId)) {
                            query.and(eq(ServiceTraffic.SERVICE_ID, serviceId));
                        }
                    }
                });

        final List<Service> services = new ArrayList<>();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(ServiceTraffic.INDEX_NAME, DownSampling.Minute);

        for (final DataPoint dataPoint : resp.getDataPoints()) {
            services.add(buildService(dataPoint, schema));
        }

        return services;
    }

    @Override
    public List<ServiceInstance> listInstances(Duration duration, String serviceId) throws IOException {
        MeasureQueryResponse resp = query(InstanceTraffic.INDEX_NAME,
                INSTANCE_TRAFFIC_TAGS,
                Collections.emptySet(),
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        if (StringUtil.isNotEmpty(serviceId)) {
                            query.and(eq(InstanceTraffic.SERVICE_ID, serviceId));
                        }
                        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(duration.getStartTimestamp());
                        query.and(gte(InstanceTraffic.LAST_PING_TIME_BUCKET, minuteTimeBucket));
                    }
                });

        final List<ServiceInstance> instances = new ArrayList<>();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(InstanceTraffic.INDEX_NAME, DownSampling.Minute);
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            instances.add(buildInstance(dataPoint, schema));
        }

        return instances;
    }

    @Override
    public ServiceInstance getInstance(String instanceId) throws IOException {
        IDManager.ServiceInstanceID.InstanceIDDefinition id = IDManager.ServiceInstanceID.analysisId(instanceId);
        MeasureQueryResponse resp = query(InstanceTraffic.INDEX_NAME,
                INSTANCE_TRAFFIC_COMPACT_TAGS,
                Collections.emptySet(),
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                            query.and(eq(InstanceTraffic.SERVICE_ID, id.getServiceId()))
                                    .and(eq(InstanceTraffic.NAME, id.getName()));
                    }
                });
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(InstanceTraffic.INDEX_NAME, DownSampling.Minute);
        return resp.size() > 0 ? buildInstance(resp.getDataPoints().get(0), schema) : null;
    }

    @Override
    public List<ServiceInstance> getInstances(List<String> instanceIds) throws IOException {
        MeasureQueryResponse resp = query(InstanceTraffic.INDEX_NAME,
            INSTANCE_TRAFFIC_TAGS,
            Collections.emptySet(),
            new QueryBuilder<MeasureQuery>() {
                @Override
                protected void apply(MeasureQuery query) {
                    query.and(in(InstanceTraffic.ID, instanceIds));
                }
            });
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(InstanceTraffic.INDEX_NAME, DownSampling.Minute);
        return resp.getDataPoints().stream().map(e -> buildInstance(e, schema)).collect(Collectors.toList());
    }

    @Override
    public List<Endpoint> findEndpoint(String keyword, String serviceId, int limit) throws IOException {
        MeasureQueryResponse resp = query(EndpointTraffic.INDEX_NAME,
                ENDPOINT_TRAFFIC_TAGS,
                Collections.emptySet(),
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        if (StringUtil.isNotEmpty(serviceId)) {
                            query.and(eq(EndpointTraffic.SERVICE_ID, serviceId));
                        }
                    }
                });

        final List<Endpoint> endpoints = new ArrayList<>();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(EndpointTraffic.INDEX_NAME, DownSampling.Minute);
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            endpoints.add(buildEndpoint(dataPoint, schema));
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            return endpoints.stream().filter(e -> e.getName().contains(keyword)).collect(Collectors.toList());
        }
        return endpoints;
    }

    @Override
    public List<Process> listProcesses(String serviceId, ProfilingSupportStatus supportStatus, long lastPingStartTimeBucket, long lastPingEndTimeBucket) throws IOException {
        MeasureQueryResponse resp = query(ProcessTraffic.INDEX_NAME,
                PROCESS_TRAFFIC_TAGS,
                Collections.emptySet(),
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.and(eq(ProcessTraffic.SERVICE_ID, serviceId));
                        if (lastPingStartTimeBucket > 0) {
                            query.and(gte(ProcessTraffic.LAST_PING_TIME_BUCKET, lastPingStartTimeBucket));
                        }
                        if (lastPingEndTimeBucket > 0) {
                            query.and(lte(ProcessTraffic.LAST_PING_TIME_BUCKET, lastPingEndTimeBucket));
                        }
                        if (supportStatus != null) {
                            query.and(eq(ProcessTraffic.PROFILING_SUPPORT_STATUS, supportStatus.value()));
                        }
                        query.and(ne(ProcessTraffic.DETECT_TYPE, ProcessDetectType.VIRTUAL.value()));
                    }
                });

        final List<Process> processes = new ArrayList<>();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(ProcessTraffic.INDEX_NAME, DownSampling.Minute);
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            processes.add(buildProcess(dataPoint, schema));
        }

        return processes;
    }

    @Override
    public List<Process> listProcesses(String serviceInstanceId, Duration duration, boolean includeVirtual) throws IOException {
        long lastPingStartTimeBucket = duration.getStartTimeBucket();
        MeasureQueryResponse resp = query(ProcessTraffic.INDEX_NAME,
                PROCESS_TRAFFIC_TAGS,
                Collections.emptySet(),
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.and(eq(ProcessTraffic.INSTANCE_ID, serviceInstanceId));
                        query.and(gte(ProcessTraffic.LAST_PING_TIME_BUCKET, lastPingStartTimeBucket));
                        if (!includeVirtual) {
                            query.and(ne(ProcessTraffic.DETECT_TYPE, ProcessDetectType.VIRTUAL.value()));
                        }
                    }
                });

        final List<Process> processes = new ArrayList<>();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(ProcessTraffic.INDEX_NAME, DownSampling.Minute);
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            processes.add(buildProcess(dataPoint, schema));
        }

        return processes;
    }

    @Override
    public List<Process> listProcesses(String agentId) throws IOException {
        MeasureQueryResponse resp = query(ProcessTraffic.INDEX_NAME,
                PROCESS_TRAFFIC_TAGS,
                Collections.emptySet(),
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.and(eq(ProcessTraffic.AGENT_ID, agentId));
                        query.and(ne(ProcessTraffic.DETECT_TYPE, ProcessDetectType.VIRTUAL.value()));
                    }
                });

        final List<Process> processes = new ArrayList<>();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(ProcessTraffic.INDEX_NAME, DownSampling.Minute);
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            processes.add(buildProcess(dataPoint, schema));
        }

        return processes;
    }

    @Override
    public long getProcessCount(String serviceId, ProfilingSupportStatus profilingSupportStatus, long lastPingStartTimeBucket, long lastPingEndTimeBucket) throws IOException {
        MeasureQueryResponse resp = query(ProcessTraffic.INDEX_NAME,
                PROCESS_TRAFFIC_TAGS,
                Collections.emptySet(),
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.and(eq(ProcessTraffic.SERVICE_ID, serviceId));
                        query.and(gte(ProcessTraffic.LAST_PING_TIME_BUCKET, lastPingStartTimeBucket));
                        query.and(eq(ProcessTraffic.PROFILING_SUPPORT_STATUS, profilingSupportStatus.value()));
                        query.and(ne(ProcessTraffic.DETECT_TYPE, ProcessDetectType.VIRTUAL.value()));
                    }
                });

        return resp.getDataPoints()
                .stream()
                .collect(Collectors.groupingBy((Function<DataPoint, String>) dataPoint -> dataPoint.getTagValue(ProcessTraffic.PROPERTIES)))
                .size();
    }

    @Override
    public long getProcessCount(String instanceId) throws IOException {
        MeasureQueryResponse resp = query(ProcessTraffic.INDEX_NAME,
                PROCESS_TRAFFIC_TAGS,
                Collections.emptySet(),
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.and(eq(ProcessTraffic.INSTANCE_ID, instanceId));
                        query.and(ne(ProcessTraffic.DETECT_TYPE, ProcessDetectType.VIRTUAL.value()));
                    }
                });

        return resp.getDataPoints()
                .stream()
                .collect(Collectors.groupingBy((Function<DataPoint, String>) dataPoint -> dataPoint.getTagValue(ProcessTraffic.PROPERTIES)))
                .size();
    }

    @Override
    public Process getProcess(String processId) throws IOException {
        MeasureQueryResponse resp = query(ProcessTraffic.INDEX_NAME,
                PROCESS_TRAFFIC_COMPACT_TAGS,
                Collections.emptySet(),
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        if (StringUtil.isNotEmpty(processId)) {
                            query.and(eq(BanyanDBConverter.ID, processId));
                        }
                    }
                });
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(ProcessTraffic.INDEX_NAME, DownSampling.Minute);

        return resp.size() > 0 ? buildProcess(resp.getDataPoints().get(0), schema) : null;
    }

    private Service buildService(DataPoint dataPoint, MetadataRegistry.Schema schema) {
        final ServiceTraffic.Builder builder = new ServiceTraffic.Builder();
        final ServiceTraffic serviceTraffic = builder.storage2Entity(new BanyanDBConverter.StorageToMeasure(schema, dataPoint));
        String serviceName = serviceTraffic.getName();
        Service service = new Service();
        service.setId(serviceTraffic.getServiceId());
        service.setName(serviceName);
        service.setShortName(serviceTraffic.getShortName());
        service.setGroup(serviceTraffic.getGroup());
        service.getLayers().add(serviceTraffic.getLayer().name());
        return service;
    }

    private ServiceInstance buildInstance(DataPoint dataPoint, MetadataRegistry.Schema schema) {
        final InstanceTraffic instanceTraffic =
                new InstanceTraffic.Builder().storage2Entity(new BanyanDBConverter.StorageToMeasure(schema, dataPoint));

        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setId(instanceTraffic.id().build());
        serviceInstance.setName(instanceTraffic.getName());
        serviceInstance.setInstanceUUID(serviceInstance.getId());

        JsonObject properties = instanceTraffic.getProperties();
        if (properties != null) {
            for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                String key = property.getKey();
                String value = property.getValue().getAsString();
                if (key.equals(LANGUAGE)) {
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

    private Endpoint buildEndpoint(DataPoint dataPoint, MetadataRegistry.Schema schema) {
        final EndpointTraffic endpointTraffic =
                new EndpointTraffic.Builder().storage2Entity(new BanyanDBConverter.StorageToMeasure(schema, dataPoint));
        Endpoint endpoint = new Endpoint();
        endpoint.setId(endpointTraffic.id().build());
        endpoint.setName(endpointTraffic.getName());
        return endpoint;
    }

    private Process buildProcess(DataPoint dataPoint, MetadataRegistry.Schema schema) {
        final ProcessTraffic processTraffic =
                new ProcessTraffic.Builder().storage2Entity(new BanyanDBConverter.StorageToMeasure(schema, dataPoint));

        Process process = new Process();
        process.setId(processTraffic.id().build());
        process.setName(processTraffic.getName());
        final String serviceId = processTraffic.getServiceId();
        process.setServiceId(serviceId);
        process.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
        final String instanceId = processTraffic.getInstanceId();
        process.setInstanceId(instanceId);
        process.setInstanceName(IDManager.ServiceInstanceID.analysisId(instanceId).getName());
        process.setAgentId(processTraffic.getAgentId());
        process.setDetectType(ProcessDetectType.valueOf(processTraffic.getDetectType()).name());
        process.setProfilingSupportStatus(ProfilingSupportStatus.valueOf(processTraffic.getProfilingSupportStatus()).name());

        JsonObject properties = processTraffic.getProperties();
        if (properties != null) {
            for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                String key = property.getKey();
                String value = property.getValue().getAsString();
                process.getAttributes().add(new Attribute(key, value));
            }
        }
        final String labelsJson = processTraffic.getLabelsJson();
        if (StringUtils.isNotEmpty(labelsJson)) {
            final List<String> labels = GSON.<List<String>>fromJson(labelsJson, ArrayList.class);
            process.getLabels().addAll(labels);
        }
        return process;
    }
}
