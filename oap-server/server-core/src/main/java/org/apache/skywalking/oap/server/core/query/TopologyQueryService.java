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
import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.manual.service.*;
import org.apache.skywalking.oap.server.core.query.entity.*;
import org.apache.skywalking.oap.server.core.query.sql.*;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.*;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.module.Service;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class TopologyQueryService implements Service {

    private static final Logger logger = LoggerFactory.getLogger(TopologyQueryService.class);

    private final ModuleManager moduleManager;
    private final IMetricQueryDAO metricQueryDAO;
    private final IUniqueQueryDAO uniqueQueryDAO;

    public TopologyQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.metricQueryDAO = moduleManager.find(StorageModule.NAME).getService(IMetricQueryDAO.class);
        this.uniqueQueryDAO = moduleManager.find(StorageModule.NAME).getService(IUniqueQueryDAO.class);
    }

    public Topology getGlobalTopology(final Step step, final long startTB, final long endTB) throws IOException {
        logger.debug("step: {}, startTimeBucket: {}, endTimeBucket: {}", step, startTB, endTB);
        List<ServiceComponent> serviceComponents = loadServiceComponent(step, startTB, endTB);
        List<ServiceMapping> serviceMappings = loadServiceMapping(step, startTB, endTB);

        List<Call> serviceRelationClientCalls = loadServiceRelationCalls(step, startTB, endTB, "service_relation_client_calls_sum");
        List<Call> serviceRelationServerCalls = loadServiceRelationCalls(step, startTB, endTB, "service_relation_server_calls_sum");

        TopologyBuilder builder = new TopologyBuilder(moduleManager);
        return builder.build(serviceComponents, serviceMappings, serviceRelationClientCalls, serviceRelationServerCalls);
    }

    public Topology getServiceTopology(final Step step, final long startTimeBucket, final long endTimeBucket,
        final String serviceId) {
        return new Topology();
    }

    private List<ServiceComponent> loadServiceComponent(final Step step, final long startTB,
        final long endTB) throws IOException {
        List<TwoIdGroup> twoIdGroups = uniqueQueryDAO.aggregation(ServiceComponentIndicator.INDEX_NAME, step, startTB, endTB,
            new Where(), ServiceComponentIndicator.SERVICE_ID, ServiceComponentIndicator.COMPONENT_ID);

        List<ServiceComponent> serviceComponents = new ArrayList<>();
        twoIdGroups.forEach(twoIdGroup -> {
            ServiceComponent serviceComponent = new ServiceComponent();
            serviceComponent.setServiceId(twoIdGroup.getId1());
            serviceComponent.setComponentId(twoIdGroup.getId2());
            serviceComponents.add(serviceComponent);
        });

        return serviceComponents;
    }

    private List<ServiceMapping> loadServiceMapping(final Step step, final long startTB,
        final long endTB) throws IOException {
        List<TwoIdGroup> twoIdGroups = uniqueQueryDAO.aggregation(ServiceMappingIndicator.INDEX_NAME, step, startTB, endTB,
            new Where(), ServiceMappingIndicator.SERVICE_ID, ServiceMappingIndicator.MAPPING_SERVICE_ID);

        List<ServiceMapping> serviceMappings = new ArrayList<>();
        twoIdGroups.forEach(twoIdGroup -> {
            ServiceMapping serviceMapping = new ServiceMapping();
            serviceMapping.setServiceId(twoIdGroup.getId1());
            serviceMapping.setMappingServiceId(twoIdGroup.getId2());
            serviceMappings.add(serviceMapping);
        });

        return serviceMappings;
    }

    private List<Call> loadServiceRelationCalls(final Step step, final long startTB, final long endTB,
        String indName) throws IOException {
        List<TwoIdGroupValue> twoIdGroupValues = metricQueryDAO.aggregation(indName, step, startTB, endTB, new Where(), "source_service_id", "dest_service_id", "value", Function.Sum);

        List<Call> clientCalls = new ArrayList<>();

        twoIdGroupValues.forEach(twoIdGroupValue -> {
            Call call = new Call();
            call.setSource(twoIdGroupValue.getId1());
            call.setTarget(twoIdGroupValue.getId2());
            call.setCalls(twoIdGroupValue.getValue().longValue());
            clientCalls.add(call);
        });

        return clientCalls;
    }
}
