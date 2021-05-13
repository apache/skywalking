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
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

public class AggregationQueryService implements Service {
    private final ModuleManager moduleManager;
    private IAggregationQueryDAO aggregationQueryDAO;

    public AggregationQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IAggregationQueryDAO getAggregationQueryDAO() {
        if (aggregationQueryDAO == null) {
            aggregationQueryDAO = moduleManager.find(StorageModule.NAME)
                                               .provider()
                                               .getService(IAggregationQueryDAO.class);
        }
        return aggregationQueryDAO;
    }

    public List<SelectedRecord> sortMetrics(TopNCondition condition, Duration duration) throws IOException {
        final String valueCName = ValueColumnMetadata.INSTANCE.getValueCName(condition.getName());
        List<KeyValue> additionalConditions = null;
        if (StringUtil.isNotEmpty(condition.getParentService())) {
            additionalConditions = new ArrayList<>(1);
            final String serviceId = IDManager.ServiceID.buildId(condition.getParentService(), condition.isNormal());
            additionalConditions.add(new KeyValue(InstanceTraffic.SERVICE_ID, serviceId));
        }
        final List<SelectedRecord> selectedRecords = getAggregationQueryDAO().sortMetrics(
            condition, valueCName, duration, additionalConditions);
        selectedRecords.forEach(selectedRecord -> {
            switch (condition.getScope()) {
                case Service:
                    selectedRecord.setName(IDManager.ServiceID.analysisId(selectedRecord.getId()).getName());
                    break;
                case ServiceInstance:
                    final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition
                        = IDManager.ServiceInstanceID.analysisId(selectedRecord.getId());
                    /**
                     * Add the service name into the name if this is global top N.
                     */
                    if (StringUtil.isEmpty(condition.getParentService())) {
                        IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition =
                            IDManager.ServiceID.analysisId(instanceIDDefinition.getServiceId());
                        selectedRecord.setName(serviceIDDefinition.getName() + " - " + instanceIDDefinition.getName());
                    } else {
                        selectedRecord.setName(instanceIDDefinition.getName());
                    }
                    break;
                case Endpoint:
                    final IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition
                        = IDManager.EndpointID.analysisId(selectedRecord.getId());
                    /**
                     * Add the service name into the name if this is global top N.
                     */
                    if (StringUtil.isEmpty(condition.getParentService())) {
                        IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition =
                            IDManager.ServiceID.analysisId(endpointIDDefinition.getServiceId());
                        selectedRecord.setName(serviceIDDefinition.getName()
                                                   + " - " + endpointIDDefinition.getEndpointName());
                    } else {
                        selectedRecord.setName(endpointIDDefinition.getEndpointName());
                    }
                    break;
                default:
                    selectedRecord.setName(Const.UNKNOWN);
            }
        });
        return selectedRecords;
    }
}
