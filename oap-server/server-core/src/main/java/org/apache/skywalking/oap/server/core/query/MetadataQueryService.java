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

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.EndpointInfo;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

public class MetadataQueryService implements org.apache.skywalking.oap.server.library.module.Service {

    private final ModuleManager moduleManager;
    private final LoadingCache<Boolean, Map<String, List<Service>>> serviceCache;
    private IMetadataQueryDAO metadataQueryDAO;

    public MetadataQueryService(ModuleManager moduleManager, CoreModuleConfig moduleConfig) {
        this.moduleManager = moduleManager;

        this.serviceCache = CacheBuilder.newBuilder()
            .maximumSize(1)
            .refreshAfterWrite(moduleConfig.getServiceCacheRefreshInterval(), TimeUnit.SECONDS)
            .build(new CacheLoader<>() {
                @Override
                public Map<String, List<Service>> load(Boolean key) throws Exception {
                    return mapAllServices();
                }
            });
    }

    private IMetadataQueryDAO getMetadataQueryDAO() {
        if (metadataQueryDAO == null) {
            metadataQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
    }

    public Set<String> listLayers() throws IOException {
        return Arrays.stream(Layer.values()).filter(layer -> layer.value() > 0).map(Layer::name).collect(Collectors.toSet());
    }

    /**
     * @return all services, key is service id, value is services with different layers.
     */
    @SneakyThrows
    public Map<String, List<Service>> listAllServices() {
        return this.serviceCache.get(true);
    }

    @SneakyThrows
    public List<Service> listServices(final String layer, final String group) throws IOException {
        return this.combineServices(this.serviceCache.get(true).values().stream().flatMap(Collection::stream)
            .filter(svc -> {
                if (StringUtils.isNotEmpty(layer) && !svc.getLayers().contains(layer)) {
                    return false;
                }
                return StringUtils.isEmpty(group) || Objects.equals(svc.getGroup(), group);
            }).collect(Collectors.toList()));
    }

    @SneakyThrows
    public Service getService(final String serviceId) throws IOException {
        final List<Service> services = this.combineServices(this.serviceCache.get(true).get(serviceId));
        return CollectionUtils.isNotEmpty(services) ? services.get(0) : null;
    }

    public ServiceInstance getInstance(final String instanceId) throws IOException {
        return getMetadataQueryDAO().getInstance(instanceId);
    }

    public List<ServiceInstance> listInstances(final Duration duration,
                                                     final String serviceId) throws IOException {
        if (duration.getStartTimestamp() >= duration.getEndTimestamp()) {
            return Collections.emptyList();
        }
        return getMetadataQueryDAO().listInstances(duration, serviceId)
                                    .stream().distinct().collect(Collectors.toList());
    }

    public List<Endpoint> findEndpoint(final String keyword, final String serviceId,
                                       final int limit) throws IOException {
        return getMetadataQueryDAO().findEndpoint(keyword, serviceId, limit)
                                    .stream().distinct().collect(Collectors.toList());
    }

    public EndpointInfo getEndpointInfo(final String endpointId) throws IOException {
        final IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(
            endpointId);
        final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
            endpointIDDefinition.getServiceId());

        EndpointInfo endpointInfo = new EndpointInfo();
        endpointInfo.setId(endpointId);
        endpointInfo.setName(endpointIDDefinition.getEndpointName());
        endpointInfo.setServiceId(endpointIDDefinition.getServiceId());
        endpointInfo.setServiceName(serviceIDDefinition.getName());
        return endpointInfo;
    }

    public List<Process> listProcesses(final Duration duration, final String instanceId) throws IOException {
        if (duration.getEndTimeBucket() < duration.getStartTimeBucket()) {
            return Collections.emptyList();
        }
        return getMetadataQueryDAO().listProcesses(instanceId, duration, true);
    }

    public Process getProcess(String processId) throws IOException {
        if (StringUtils.isEmpty(processId)) {
            return null;
        }
        return getMetadataQueryDAO().getProcess(processId);
    }

    public Long estimateProcessScale(String serviceId, List<String> labels) throws IOException {
        if (StringUtils.isEmpty(serviceId)) {
            return 0L;
        }
        final long endTimestamp = System.currentTimeMillis();
        final long startTimestamp = endTimestamp - TimeUnit.MINUTES.toMillis(10);
        final List<Process> processes = getMetadataQueryDAO().listProcesses(serviceId,
                ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, TimeBucket.getTimeBucket(startTimestamp, DownSampling.Minute),
                TimeBucket.getTimeBucket(endTimestamp, DownSampling.Minute));
        return CollectionUtils.isEmpty(processes) ?
                0L :
                processes.stream().filter(p -> p.getLabels().containsAll(labels)).count();
    }

    private Map<String, List<Service>> mapAllServices() throws Exception {
        final List<Service> services = getMetadataQueryDAO().listServices();
        return services.stream().peek(service -> {
            if (service.getGroup() == null) {
                service.setGroup(Const.EMPTY_STRING);
            }
        }).collect(Collectors.toMap(Service::getId, s -> new ArrayList<>(List.of(s)), (s1, s2) -> {
            s1.addAll(s2);
            return s1;
        }));
    }

    private List<Service> combineServices(List<Service> services) {
        if (CollectionUtils.isEmpty(services)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(services.stream()
                                       .collect(Collectors.toMap(Service::getName, service -> service,
                                                                 (s1, s2) -> {
                                                                     s1.getLayers().addAll(s2.getLayers());
                                                                     return s1;
                                                                 }
                                       )).values());
    }
}
