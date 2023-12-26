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
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.HierarchyDefinitionService;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelation;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelation;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

@Slf4j
public class HierarchyService implements Service {
    private final ModuleManager moduleManager;
    private final boolean isEnableHierarchy;
    private SourceReceiver sourceReceiver;
    private Map<String, List<String>> hierarchyDefinition;

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

    private Map<String, List<String>> getHierarchyDefinition() {
        if (hierarchyDefinition == null) {
            hierarchyDefinition = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(HierarchyDefinitionService.class).getHierarchyDefinition();
        }
        return hierarchyDefinition;
    }

    /**
     * Build the hierarchy relation between the 2 services. the `serviceLayer` and `relatedServiceLayer` hierarchy
     * relations should be defined in `config/layer-hierarchy.yml`.
     */
    public void toServiceHierarchyRelation(String serviceName,
                                           Layer serviceLayer,
                                           String relatedServiceName,
                                           Layer relatedServiceLayer) {
        if (!this.isEnableHierarchy) {
            return;
        }
        checkHierarchyDefinition(serviceLayer, relatedServiceLayer);
        ServiceHierarchyRelation serviceHierarchy = new ServiceHierarchyRelation();
        serviceHierarchy.setServiceName(serviceName);
        serviceHierarchy.setServiceLayer(serviceLayer);
        serviceHierarchy.setRelatedServiceName(relatedServiceName);
        serviceHierarchy.setRelatedServiceLayer(relatedServiceLayer);
        serviceHierarchy.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        this.getSourceReceiver().receive(serviceHierarchy);
    }

    /**
     * Build the hierarchy relation between the 2 instances. the `serviceLayer` and `relatedServiceLayer` hierarchy
     * relations should be defined in `config/layer-hierarchy.yml`.
     */
    public void toInstanceHierarchyRelation(String instanceName,
                                            String serviceName,
                                            Layer serviceLayer,
                                            String relatedInstanceName,
                                            String relatedServiceName,
                                            Layer relatedServiceLayer) {
        if (!this.isEnableHierarchy) {
            return;
        }
        checkHierarchyDefinition(serviceLayer, relatedServiceLayer);
        InstanceHierarchyRelation instanceHierarchy = new InstanceHierarchyRelation();
        instanceHierarchy.setInstanceName(instanceName);
        instanceHierarchy.setServiceName(serviceName);
        instanceHierarchy.setServiceLayer(serviceLayer);
        instanceHierarchy.setRelatedInstanceName(relatedInstanceName);
        instanceHierarchy.setRelatedServiceName(relatedServiceName);
        instanceHierarchy.setRelatedServiceLayer(relatedServiceLayer);
        instanceHierarchy.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        this.getSourceReceiver().receive(instanceHierarchy);
    }

    private void checkHierarchyDefinition(Layer serviceLayer, Layer relatedServiceLayer) {
        List<String> lowerLayers = getHierarchyDefinition().get(serviceLayer.name());
        List<String> relatedLowerLayers = getHierarchyDefinition().get(relatedServiceLayer.name());
        if (lowerLayers == null || relatedLowerLayers == null) {
            log.error("serviceLayer " + serviceLayer.name() + " or relatedServiceLayer " + relatedServiceLayer.name()
                          + " is not defined in layer-hierarchy.yml.");
        }

        if (!lowerLayers.contains(relatedServiceLayer.name()) || !relatedLowerLayers.contains(serviceLayer.name())) {
            log.error("serviceLayer " + serviceLayer.name() + " and relatedServiceLayer " + relatedServiceLayer.name()
                          + " should have the hierarchy relation in layer-hierarchy.yml.");
        }
    }
}
