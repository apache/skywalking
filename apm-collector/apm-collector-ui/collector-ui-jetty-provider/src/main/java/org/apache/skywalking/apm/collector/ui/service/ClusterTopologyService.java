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

package org.apache.skywalking.apm.collector.ui.service;

import java.util.*;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IComponentLibraryCatalogService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.table.MetricSource;
import org.apache.skywalking.apm.collector.storage.ui.common.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ClusterTopologyService {

    private static final Logger logger = LoggerFactory.getLogger(ClusterTopologyService.class);

    private final IApplicationComponentUIDAO applicationComponentUIDAO;
    private final IApplicationMappingUIDAO applicationMappingUIDAO;
    private final IApplicationMetricUIDAO applicationMetricUIDAO;
    private final IApplicationReferenceMetricUIDAO applicationReferenceMetricUIDAO;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;
    private final ModuleManager moduleManager;

    public ClusterTopologyService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.applicationComponentUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationComponentUIDAO.class);
        this.applicationMappingUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationMappingUIDAO.class);
        this.applicationMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationMetricUIDAO.class);
        this.applicationReferenceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceMetricUIDAO.class);
        this.componentLibraryCatalogService = moduleManager.find(ConfigurationModule.NAME).getService(IComponentLibraryCatalogService.class);
    }

    public Topology getClusterTopology(Step step, long startTimeBucket, long endTimeBucket, long startSecondTimeBucket,
        long endSecondTimeBucket) {
        logger.debug("startTimeBucket: {}, endTimeBucket: {}, startSecondTimeBucket: {}, endSecondTimeBucket: {}", startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket);
        List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents = applicationComponentUIDAO.load(step, startTimeBucket, endTimeBucket);
        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings = applicationMappingUIDAO.load(step, startTimeBucket, endTimeBucket);

        Map<Integer, String> components = new HashMap<>();
        applicationComponents.forEach(component -> components.put(component.getApplicationId(), this.componentLibraryCatalogService.getComponentName(component.getComponentId())));

        List<IApplicationMetricUIDAO.ApplicationMetric> applicationMetrics = applicationMetricUIDAO.getApplications(step, startTimeBucket, endTimeBucket, MetricSource.Callee);

        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> callerReferenceMetric = applicationReferenceMetricUIDAO.getReferences(step, startTimeBucket, endTimeBucket, MetricSource.Caller);
        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> calleeReferenceMetric = applicationReferenceMetricUIDAO.getReferences(step, startTimeBucket, endTimeBucket, MetricSource.Callee);

        TopologyBuilder builder = new TopologyBuilder(moduleManager);

        return builder.build(applicationComponents, applicationMappings, applicationMetrics, callerReferenceMetric, calleeReferenceMetric, startSecondTimeBucket, endSecondTimeBucket);
    }
}
