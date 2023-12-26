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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.config.HierarchyDefinitionService;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.query.type.HierarchyInstanceRelation;
import org.apache.skywalking.oap.server.core.query.type.HierarchyRelatedInstance;
import org.apache.skywalking.oap.server.core.query.type.HierarchyRelatedService;
import org.apache.skywalking.oap.server.core.query.type.HierarchyServiceRelation;
import org.apache.skywalking.oap.server.core.query.type.InstanceHierarchy;
import org.apache.skywalking.oap.server.core.query.type.ServiceHierarchy;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IHierarchyQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

@Slf4j
public class HierarchyQueryService implements Service {
    private final ModuleManager moduleManager;
    private final boolean isEnableHierarchy;
    private IHierarchyQueryDAO hierarchyQueryDAO;
    private Map<String, List<String>> hierarchyDefinition;
    private LoadingCache<Boolean, Map<HierarchyRelatedService, ServiceRelations>> serviceHierarchyCache;

    public HierarchyQueryService(ModuleManager moduleManager, CoreModuleConfig moduleConfig) {
        this.moduleManager = moduleManager;
        this.isEnableHierarchy = moduleConfig.isEnableHierarchy();
        if (moduleConfig.isEnableHierarchy()) {
            this.serviceHierarchyCache =
                CacheBuilder.newBuilder()
                            .maximumSize(1)
                            .refreshAfterWrite(moduleConfig.getServiceCacheRefreshInterval(), TimeUnit.SECONDS)
                            .build(
                                new CacheLoader<>() {
                                    @Override
                                    public Map<HierarchyRelatedService, ServiceRelations> load(final Boolean key) throws Exception {
                                        return mapServiceHierarchy();
                                    }
                                });
        }
    }

    private IHierarchyQueryDAO getHierarchyQueryDAO() {
        if (hierarchyQueryDAO == null) {
            this.hierarchyQueryDAO = moduleManager.find(StorageModule.NAME)
                                                  .provider()
                                                  .getService(IHierarchyQueryDAO.class);
        }
        return hierarchyQueryDAO;
    }

    private Map<String, List<String>> getHierarchyDefinition() {
        if (hierarchyDefinition == null) {
            hierarchyDefinition = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(HierarchyDefinitionService.class).getHierarchyDefinition();
        }
        return hierarchyDefinition;
    }

    private Map<HierarchyRelatedService, ServiceRelations> mapServiceHierarchy() throws Exception {
        List<ServiceHierarchyRelationTraffic> traffics = getHierarchyQueryDAO().readAllServiceHierarchyRelations();
        Map<HierarchyRelatedService, ServiceRelations> serviceRelationsMap = new HashMap<>();

        for (ServiceHierarchyRelationTraffic traffic : traffics) {
            HierarchyRelatedService service = new HierarchyRelatedService();
            service.setId(traffic.getServiceId());
            service.setName(IDManager.ServiceID.analysisId(traffic.getServiceId()).getName());
            service.setLayer(traffic.getServicelayer().name());
            HierarchyRelatedService relatedService = new HierarchyRelatedService();
            relatedService.setId(traffic.getRelatedServiceId());
            relatedService.setName(IDManager.ServiceID.analysisId(traffic.getRelatedServiceId()).getName());
            relatedService.setLayer(traffic.getRelatedServiceLayer().name());

            ServiceRelations serviceRelations = serviceRelationsMap.computeIfAbsent(
                service, k -> new ServiceRelations());
            ServiceRelations relationServiceRelations = serviceRelationsMap.computeIfAbsent(
                relatedService, k -> new ServiceRelations());
            List<String> lowerLayers = getHierarchyDefinition().getOrDefault(
                traffic.getServicelayer().name(), new ArrayList<>());
            List<String> relatedLowerLayers = getHierarchyDefinition().getOrDefault(
                traffic.getRelatedServiceLayer().name(), new ArrayList<>());

            //should build the relations in 2 direction
            if (lowerLayers.contains(traffic.getRelatedServiceLayer().name())) {
                serviceRelations.getLowerServices().add(relatedService);
                relationServiceRelations.getUpperServices().add(service);
            } else if (relatedLowerLayers.contains(traffic.getServicelayer().name())) {
                serviceRelations.getUpperServices().add(relatedService);
                relationServiceRelations.getLowerServices().add(service);
            }
        }
        return serviceRelationsMap;
    }

    public ServiceHierarchy getServiceHierarchy(String serviceId, String layer) throws Exception {
        if (!this.isEnableHierarchy) {
            log.warn("CoreModuleConfig config {enableHierarchy} is false, return empty ServiceHierarchy.");
            return new ServiceHierarchy();
        }
        ServiceHierarchy hierarchy = new ServiceHierarchy();
        HierarchyRelatedService self = new HierarchyRelatedService();
        self.setId(serviceId);
        self.setName(IDManager.ServiceID.analysisId(serviceId).getName());
        self.setLayer(layer);
        Map<HierarchyRelatedService, ServiceRelations> serviceRelationsMap = serviceHierarchyCache.get(true);
        ServiceRelations serviceRelations = serviceRelationsMap.getOrDefault(self, new ServiceRelations());

        serviceRelations.getLowerServices().forEach(relatedService -> {
            hierarchy.getRelations().add(new HierarchyServiceRelation(self, relatedService));
        });
        serviceRelations.getUpperServices().forEach(relatedService -> {
            hierarchy.getRelations().add(new HierarchyServiceRelation(relatedService, self));
        });

        return hierarchy;
    }

    public InstanceHierarchy getInstanceHierarchy(String instanceId, String layer) throws Exception {
        if (!this.isEnableHierarchy) {
            log.warn("CoreModuleConfig config {enableHierarchy} is false, return empty InstanceHierarchy.");
            return new InstanceHierarchy();
        }
        InstanceHierarchy hierarchy = new InstanceHierarchy();
        List<InstanceHierarchyRelationTraffic> traffics = getHierarchyQueryDAO().readInstanceHierarchyRelations(
            instanceId, layer);

        for (InstanceHierarchyRelationTraffic traffic : traffics) {
            HierarchyRelatedInstance instance = new HierarchyRelatedInstance();
            instance.setId(traffic.getInstanceId());
            instance.setName(IDManager.ServiceInstanceID.analysisId(traffic.getInstanceId()).getName());
            instance.setLayer(traffic.getServicelayer().name());
            HierarchyRelatedInstance relatedInstance = new HierarchyRelatedInstance();
            relatedInstance.setId(traffic.getRelatedInstanceId());
            relatedInstance.setName(IDManager.ServiceInstanceID.analysisId(traffic.getRelatedInstanceId()).getName());
            relatedInstance.setLayer(traffic.getRelatedServiceLayer().name());
            List<String> lowerLayers = getHierarchyDefinition().getOrDefault(
                traffic.getServicelayer().name(), new ArrayList<>());
            List<String> relatedLowerLayers = getHierarchyDefinition().getOrDefault(
                traffic.getRelatedServiceLayer().name(), new ArrayList<>());

            //should build the relations in 2 direction
            if (lowerLayers.contains(traffic.getRelatedServiceLayer().name())) {
                hierarchy.getRelations().add(new HierarchyInstanceRelation(instance, relatedInstance));
            } else if (relatedLowerLayers.contains(traffic.getServicelayer().name())) {
                hierarchy.getRelations().add(new HierarchyInstanceRelation(relatedInstance, instance));
            }
        }

        return hierarchy;
    }

    @Data
    static class ServiceRelations {
        private List<HierarchyRelatedService> upperServices = new ArrayList<>();
        private List<HierarchyRelatedService> lowerServices = new ArrayList<>();
    }
}
