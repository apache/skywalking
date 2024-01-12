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
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.config.HierarchyDefinitionService;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.HierarchyInstanceRelation;
import org.apache.skywalking.oap.server.core.query.type.HierarchyRelatedInstance;
import org.apache.skywalking.oap.server.core.query.type.HierarchyRelatedService;
import org.apache.skywalking.oap.server.core.query.type.HierarchyServiceRelation;
import org.apache.skywalking.oap.server.core.query.type.InstanceHierarchy;
import org.apache.skywalking.oap.server.core.query.type.LayerLevel;
import org.apache.skywalking.oap.server.core.query.type.ServiceHierarchy;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IHierarchyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

@Slf4j
public class HierarchyQueryService implements Service {
    private final ModuleManager moduleManager;
    private final boolean isEnableHierarchy;
    private IHierarchyQueryDAO hierarchyQueryDAO;
    private IMetadataQueryDAO metadataQueryDAO;
    private Map<String, Map<String, HierarchyDefinitionService.MatchingRule>> hierarchyDefinition;
    private Map<String, Integer> layerLevels;
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

    private IMetadataQueryDAO getMetadataQueryDAO() {
        if (metadataQueryDAO == null) {
            this.metadataQueryDAO = moduleManager.find(StorageModule.NAME)
                                                     .provider()
                                                     .getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
    }

    private Map<String, Map<String, HierarchyDefinitionService.MatchingRule>> getHierarchyDefinition() {
        if (hierarchyDefinition == null) {
            hierarchyDefinition = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(HierarchyDefinitionService.class).getHierarchyDefinition();
        }
        return hierarchyDefinition;
    }

    private Map<String, Integer> getLayerLevels() {
        if (layerLevels == null) {
            layerLevels = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(HierarchyDefinitionService.class).getLayerLevels();
        }
        return layerLevels;
    }

    private Map<HierarchyRelatedService, ServiceRelations> mapServiceHierarchy() throws Exception {
        List<ServiceHierarchyRelationTraffic> traffics = getHierarchyQueryDAO().readAllServiceHierarchyRelations();
        Map<HierarchyRelatedService, ServiceRelations> serviceRelationsMap = new HashMap<>();

        for (ServiceHierarchyRelationTraffic traffic : traffics) {
            HierarchyRelatedService service = new HierarchyRelatedService();
            IDManager.ServiceID.ServiceIDDefinition serviceIdDef = IDManager.ServiceID.analysisId(
                traffic.getServiceId());
            service.setId(traffic.getServiceId());
            service.setName(serviceIdDef.getName());
            service.setLayer(traffic.getServiceLayer().name());
            service.setNormal(serviceIdDef.isReal());
            HierarchyRelatedService relatedService = new HierarchyRelatedService();
            IDManager.ServiceID.ServiceIDDefinition relatedServiceIdDef = IDManager.ServiceID.analysisId(
                traffic.getRelatedServiceId());
            relatedService.setId(traffic.getRelatedServiceId());
            relatedService.setName(relatedServiceIdDef.getName());
            relatedService.setLayer(traffic.getRelatedServiceLayer().name());
            relatedService.setNormal(relatedServiceIdDef.isReal());

            ServiceRelations serviceRelations = serviceRelationsMap.computeIfAbsent(
                service, k -> new ServiceRelations());
            ServiceRelations relationServiceRelations = serviceRelationsMap.computeIfAbsent(
                relatedService, k -> new ServiceRelations());
            Map<String, HierarchyDefinitionService.MatchingRule> lowerLayers = getHierarchyDefinition().getOrDefault(
                traffic.getServiceLayer().name(), new HashMap<>());
            if (lowerLayers.containsKey(traffic.getRelatedServiceLayer().name())) {
                serviceRelations.getLowerServices().add(relatedService);
                relationServiceRelations.getUpperServices().add(service);
            }
        }
        return serviceRelationsMap;
    }

    private void buildServiceRelation(ServiceHierarchy hierarchy, HierarchyRelatedService self, int maxDepth, HierarchyDirection direction) throws ExecutionException {
        if (maxDepth < 1) {
            return;
        }
        maxDepth--;
        Map<HierarchyRelatedService, ServiceRelations> serviceRelationsMap = serviceHierarchyCache.get(true);
        ServiceRelations serviceRelations = serviceRelationsMap.getOrDefault(self, new ServiceRelations());

        if (serviceRelations.getLowerServices().isEmpty() && serviceRelations.getUpperServices().isEmpty()) {
            return;
        }

        if (direction == HierarchyDirection.LOWER || direction == HierarchyDirection.All) {
            for (HierarchyRelatedService lowerService : serviceRelations.getLowerServices()) {
                HierarchyServiceRelation relation = new HierarchyServiceRelation(self, lowerService);
                if (!hierarchy.getRelations().add(relation)) {
                    continue;
                }
                buildServiceRelation(hierarchy, lowerService, maxDepth, direction);
            }
        }
        if (direction == HierarchyDirection.UPPER || direction == HierarchyDirection.All) {
            for (HierarchyRelatedService upperService : serviceRelations.getUpperServices()) {
                HierarchyServiceRelation relation = new HierarchyServiceRelation(upperService, self);
                if (!hierarchy.getRelations().add(relation)) {
                    continue;
                }
                buildServiceRelation(hierarchy, upperService, maxDepth, direction);
            }
        }
    }

    private ServiceHierarchy getServiceHierarchy(String serviceId, String layer, int maxDepth, HierarchyDirection direction) throws Exception {
        ServiceHierarchy hierarchy = new ServiceHierarchy();
        HierarchyRelatedService self = new HierarchyRelatedService();
        self.setId(serviceId);
        self.setName(IDManager.ServiceID.analysisId(serviceId).getName());
        self.setLayer(layer);
        self.setNormal(Layer.nameOf(layer).isNormal());
        buildServiceRelation(hierarchy, self, maxDepth, direction);
        return hierarchy;
    }

    /**
     * @return return the related service hierarchy recursively, e.g. A-B-C, query A will return A-B, B-C
     */
    public ServiceHierarchy getServiceHierarchy(String serviceId, String layer) throws Exception {
        if (!this.isEnableHierarchy) {
            log.warn("CoreModuleConfig config {enableHierarchy} is false, return empty ServiceHierarchy.");
            return new ServiceHierarchy();
        }
        //build relation recursively, set max depth to 10
        return getServiceHierarchy(serviceId, layer, 10, HierarchyDirection.All);
    }

    public InstanceHierarchy getInstanceHierarchy(String instanceId, String layer) throws Exception {
        if (!this.isEnableHierarchy) {
            log.warn("CoreModuleConfig config {enableHierarchy} is false, return empty InstanceHierarchy.");
            return new InstanceHierarchy();
        }
        ServiceInstance self = getMetadataQueryDAO().getInstance(instanceId);
        if (self == null) {
            return new InstanceHierarchy();
        }

        List<HierarchyInstanceRelation> relations = new ArrayList<>();
        //build from service hierarchy and instance traffic
        IDManager.ServiceInstanceID.InstanceIDDefinition idDefinition = IDManager.ServiceInstanceID.analysisId(
            instanceId);
        IDManager.ServiceID.ServiceIDDefinition serviceIdDefinition = IDManager.ServiceID.analysisId(
            idDefinition.getServiceId());
        //instance is only query 1 depth of service hierarchy, set max depth to 1
        ServiceHierarchy serviceHierarchy = getServiceHierarchy(idDefinition.getServiceId(), layer, 1, HierarchyDirection.All);

        Optional<Attribute> host = self.getAttributes()
                                           .stream()
                                           .filter(hostAttrFilter())
                                           .findFirst();
        Optional<ServiceInstance> lower;
        Optional<ServiceInstance> upper;

        for (HierarchyServiceRelation serviceRelation : serviceHierarchy.getRelations()) {
            //if the service relation is lower/upper, then the instance relation is upper/lower
            List<ServiceInstance> lowerCandidates = getMetadataQueryDAO().listInstances(
                null,
                serviceRelation.getLowerService()
                               .getId()
            );
            List<ServiceInstance> upperCandidates = getMetadataQueryDAO().listInstances(
                null,
                serviceRelation.getUpperService()
                               .getId()
            );
            lower = lowerCandidates.stream()
                                   .filter(relatedInstanceFilter(self, host))
                                   .findFirst();
            upper = upperCandidates.stream()
                                   .filter(relatedInstanceFilter(self, host))
                                   .findFirst();
            HierarchyRelatedInstance instance = new HierarchyRelatedInstance();
            instance.setId(self.getId());
            instance.setName(self.getName());
            instance.setServiceId(idDefinition.getServiceId());
            instance.setServiceName(serviceIdDefinition.getName());
            instance.setNormal(serviceIdDefinition.isReal());
            instance.setLayer(layer);
            //The instances could be same but the service layer is different
            if (lower.isPresent() && !layer.equals(serviceRelation.getLowerService().getLayer())) {
                HierarchyRelatedInstance relatedInstance = new HierarchyRelatedInstance();
                relatedInstance.setId(lower.get().getId());
                relatedInstance.setName(lower.get().getName());
                relatedInstance.setServiceId(serviceRelation.getLowerService().getId());
                relatedInstance.setServiceName(serviceRelation.getLowerService().getName());
                relatedInstance.setLayer(serviceRelation.getLowerService().getLayer());
                relatedInstance.setNormal(serviceRelation.getLowerService().isNormal());
                relations.add(new HierarchyInstanceRelation(instance, relatedInstance));
            }

            if (upper.isPresent() && !layer.equals(serviceRelation.getUpperService().getLayer())) {
                HierarchyRelatedInstance relatedInstance = new HierarchyRelatedInstance();
                relatedInstance.setId(upper.get().getId());
                relatedInstance.setName(upper.get().getName());
                relatedInstance.setServiceId(serviceRelation.getUpperService().getId());
                relatedInstance.setServiceName(serviceRelation.getUpperService().getName());
                relatedInstance.setLayer(serviceRelation.getUpperService().getLayer());
                relatedInstance.setNormal(serviceRelation.getUpperService().isNormal());
                relations.add(new HierarchyInstanceRelation(relatedInstance, instance));
            }
        }

        //build from storage directly
        List<InstanceHierarchyRelationTraffic> traffics = getHierarchyQueryDAO().readInstanceHierarchyRelations(
            instanceId, layer);

        for (InstanceHierarchyRelationTraffic traffic : traffics) {
            HierarchyRelatedInstance instance = new HierarchyRelatedInstance();
            IDManager.ServiceInstanceID.InstanceIDDefinition idDef = IDManager.ServiceInstanceID.analysisId(
                instanceId);
            IDManager.ServiceID.ServiceIDDefinition serviceIdDef = IDManager.ServiceID.analysisId(
                idDefinition.getServiceId());
            instance.setId(traffic.getInstanceId());
            instance.setName(idDef.getName());
            instance.setServiceId(idDef.getServiceId());
            instance.setServiceName(serviceIdDef.getName());
            instance.setLayer(traffic.getServiceLayer().name());
            instance.setNormal(serviceIdDef.isReal());
            HierarchyRelatedInstance relatedInstance = new HierarchyRelatedInstance();
            IDManager.ServiceInstanceID.InstanceIDDefinition relatedIdDef = IDManager.ServiceInstanceID.analysisId(
                traffic.getRelatedInstanceId());
            IDManager.ServiceID.ServiceIDDefinition relatedServiceIdDef = IDManager.ServiceID.analysisId(
                relatedIdDef.getServiceId());
            relatedInstance.setId(traffic.getRelatedInstanceId());
            relatedInstance.setName(relatedIdDef.getName());
            relatedInstance.setServiceId(relatedIdDef.getServiceId());
            relatedInstance.setServiceName(relatedServiceIdDef.getName());
            relatedInstance.setLayer(traffic.getRelatedServiceLayer().name());
            relatedInstance.setNormal(relatedServiceIdDef.isReal());
            Map<String, HierarchyDefinitionService.MatchingRule> lowerLayers = getHierarchyDefinition().get(
                traffic.getServiceLayer().name());
            if (lowerLayers != null && lowerLayers.containsKey(traffic.getRelatedServiceLayer().name())) {
                relations.add(new HierarchyInstanceRelation(instance, relatedInstance));
            }
        }
        InstanceHierarchy hierarchy = new InstanceHierarchy();
        hierarchy.setRelations(relations.stream().distinct().collect(Collectors.toList()));
        return hierarchy;
    }

    public List<LayerLevel> listLayerLevels() {
        return getLayerLevels().entrySet()
                               .stream()
                               .map(entry -> new LayerLevel(entry.getKey(), entry.getValue()))
                               .collect(Collectors.toList());
    }

    private Predicate<Attribute> hostAttrFilter() {
        return attribute -> attribute.getName().equalsIgnoreCase("pod")
            || attribute.getName().equalsIgnoreCase("hostname");
    }

    private Predicate<ServiceInstance> relatedInstanceFilter(ServiceInstance instance, Optional<Attribute> hostAttr) {
        return candidate -> {
            // both names equals
            if (candidate.getName().equals(instance.getName())) {
                return true;
            }
            if (hostAttr.isPresent()) {
                // both hosts equals
                if (candidate.getAttributes().contains(hostAttr.get())) {
                    return true;
                }
                // name equals host
                if (candidate.getName().equals(hostAttr.get().getValue())) {
                    return true;
                }
                // host equals name
                return candidate.getAttributes()
                                .stream()
                                .filter(hostAttrFilter())
                                .anyMatch(attr -> attr.getValue().equals(instance.getName()));
            }
            return false;
        };
    }

    @Data
    static class ServiceRelations {
        private List<HierarchyRelatedService> upperServices = new ArrayList<>();
        private List<HierarchyRelatedService> lowerServices = new ArrayList<>();
    }

    enum HierarchyDirection {
        All,
        UPPER,
        LOWER
    }
}
