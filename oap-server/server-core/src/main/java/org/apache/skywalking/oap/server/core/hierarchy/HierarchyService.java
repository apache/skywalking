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

package org.apache.skywalking.oap.server.core.hierarchy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.HierarchyDefinitionService;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelation;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelation;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.RunnableWithExceptionProtection;

@Slf4j
public class HierarchyService implements org.apache.skywalking.oap.server.library.module.Service {
    private final ModuleManager moduleManager;
    private final boolean isEnableHierarchy;
    private SourceReceiver sourceReceiver;
    private MetadataQueryService metadataQueryService;
    private Map<String, Map<String, HierarchyDefinitionService.MatchingRule>> hierarchyDefinition;

    public HierarchyService(ModuleManager moduleManager, CoreModuleConfig moduleConfig) {
        this.moduleManager = moduleManager;
        this.isEnableHierarchy = moduleConfig.isEnableHierarchy();
    }

    private SourceReceiver getSourceReceiver() {
        if (sourceReceiver == null) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        }
        return sourceReceiver;

    }

    private Map<String, Map<String, HierarchyDefinitionService.MatchingRule>> getHierarchyDefinition() {
        if (hierarchyDefinition == null) {
            hierarchyDefinition = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(HierarchyDefinitionService.class).getHierarchyDefinition();
        }
        return hierarchyDefinition;
    }

    private MetadataQueryService getMetadataQueryService() {
        if (metadataQueryService == null) {
            this.metadataQueryService = moduleManager.find(CoreModule.NAME)
                                                     .provider()
                                                     .getService(MetadataQueryService.class);
        }
        return metadataQueryService;
    }

    /**
     * Build the hierarchy relation between the 2 services. the `serviceLayer` and `relatedServiceLayer` hierarchy
     * relations should be defined in `config/hierarchy-definition.yml`.
     *
     * @param upperServiceName         the name of the service
     * @param upperServiceLayer        the layer of the service
     * @param lowerServiceName  the name of the lower service
     * @param lowerServiceLayer the layer of the lower service
     */
    public void toServiceHierarchyRelation(String upperServiceName,
                                           Layer upperServiceLayer,
                                           String lowerServiceName,
                                           Layer lowerServiceLayer) {
        if (!this.isEnableHierarchy) {
            return;
        }
        Map<String, HierarchyDefinitionService.MatchingRule> lowerLayers = getHierarchyDefinition().get(upperServiceLayer.name());
        if (lowerLayers == null || !lowerLayers.containsKey(lowerServiceLayer.name())) {
            log.error("upperServiceLayer " + upperServiceLayer.name() + " or lowerServiceLayer " + lowerServiceLayer.name()
                          + " is not defined in hierarchy-definition.yml.");
            return;
        }
        autoMatchingServiceRelation(upperServiceName, upperServiceLayer, lowerServiceName, lowerServiceLayer);
    }

    /**
     * Build the hierarchy relation between the 2 instances. the `serviceLayer` and `relatedServiceLayer` hierarchy
     * relations should be defined in `config/hierarchy-definition.yml`.
     *
     * @param upperInstanceName        the name of the upper instance
     * @param upperServiceName         the name of the upper service
     * @param upperServiceLayer        the layer of the upper service
     * @param lowerInstanceName the name of the lower related instance
     * @param lowerServiceName  the name of the lower related service
     * @param lowerServiceLayer the layer of the lower related service
     */
    public void toInstanceHierarchyRelation(String upperInstanceName,
                                            String upperServiceName,
                                            Layer upperServiceLayer,
                                            String lowerInstanceName,
                                            String lowerServiceName,
                                            Layer lowerServiceLayer) {
        if (!this.isEnableHierarchy) {
            return;
        }
        Map<String, HierarchyDefinitionService.MatchingRule> lowerLayers = getHierarchyDefinition().get(upperServiceLayer.name());
        if (lowerLayers == null || !lowerLayers.containsKey(lowerServiceLayer.name())) {
            log.error("upperServiceLayer " + upperServiceLayer.name() + " or lowerServiceLayer " + lowerServiceLayer.name()
                          + " is not defined in hierarchy-definition.yml.");
            return;
        }

        buildInstanceHierarchyRelation(upperInstanceName, upperServiceName, upperServiceLayer, lowerInstanceName,
                                           lowerServiceName, lowerServiceLayer);
    }

    public void startAutoMatchingServiceHierarchy() {
        if (!this.isEnableHierarchy) {
            return;
        }
        Executors.newSingleThreadScheduledExecutor()
                 .scheduleWithFixedDelay(
                     new RunnableWithExceptionProtection(this::autoMatchingServiceRelation, t -> log.error(
                         "Scheduled auto matching service hierarchy from service traffic failure.", t)), 30, 20, TimeUnit.SECONDS);
    }

    private void autoMatchingServiceRelation(String upperServiceName,
                                             Layer upperServiceLayer,
                                             String lowerServiceName,
                                             Layer lowerServiceLayer) {
        ServiceHierarchyRelation serviceHierarchy = new ServiceHierarchyRelation();
        serviceHierarchy.setServiceName(upperServiceName);
        serviceHierarchy.setServiceLayer(upperServiceLayer);
        serviceHierarchy.setRelatedServiceName(lowerServiceName);
        serviceHierarchy.setRelatedServiceLayer(lowerServiceLayer);
        serviceHierarchy.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        this.getSourceReceiver().receive(serviceHierarchy);
    }

    private void buildInstanceHierarchyRelation(String upperInstanceName,
                                                String upperServiceName,
                                                Layer upperServiceLayer,
                                                String lowerInstanceName,
                                                String lowerServiceName,
                                                Layer lowerServiceLayer) {
        InstanceHierarchyRelation instanceHierarchy = new InstanceHierarchyRelation();
        instanceHierarchy.setInstanceName(upperInstanceName);
        instanceHierarchy.setServiceName(upperServiceName);
        instanceHierarchy.setServiceLayer(upperServiceLayer);
        instanceHierarchy.setRelatedInstanceName(lowerInstanceName);
        instanceHierarchy.setRelatedServiceName(lowerServiceName);
        instanceHierarchy.setRelatedServiceLayer(lowerServiceLayer);
        instanceHierarchy.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        this.getSourceReceiver().receive(instanceHierarchy);
    }

    private void autoMatchingServiceRelation() {
        List<Service> allServices = getMetadataQueryService().listAllServices()
                                                             .values()
                                                             .stream()
                                                             .flatMap(List::stream)
                                                             .collect(Collectors.toList());
        if (allServices.size() > 1) {
            for (int i = 0; i < allServices.size(); i++) {
                for (int j = i + 1; j < allServices.size(); j++) {
                    Service service = allServices.get(i);
                    Service comparedService = allServices.get(j);
                    String serviceLayer = service.getLayers().iterator().next();
                    String comparedServiceLayer = comparedService.getLayers().iterator().next();
                    Map<String, HierarchyDefinitionService.MatchingRule> lowerLayers = getHierarchyDefinition().get(
                        serviceLayer);
                    Map<String, HierarchyDefinitionService.MatchingRule> comparedLowerLayers = getHierarchyDefinition().get(
                        comparedServiceLayer);
                    if (lowerLayers != null
                        && lowerLayers.get(comparedServiceLayer) != null
                        && lowerLayers.get(comparedServiceLayer)
                                      .getClosure()
                                      .call(service, comparedService)) {
                        autoMatchingServiceRelation(service.getName(), Layer.nameOf(serviceLayer),
                                                    comparedService.getName(),
                                                    Layer.nameOf(comparedServiceLayer)
                        );
                    } else if (comparedLowerLayers != null
                        && comparedLowerLayers.get(serviceLayer) != null
                        && comparedLowerLayers.get(serviceLayer)
                                             .getClosure()
                                             .call(comparedService, service)) {
                        autoMatchingServiceRelation(comparedService.getName(), Layer.nameOf(comparedServiceLayer),
                                                    service.getName(),
                                                    Layer.nameOf(serviceLayer)
                        );
                    }
                }
            }
        }
    }
}
