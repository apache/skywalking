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

package org.apache.skywalking.apm.collector.analysis.segment.parser.provider.parser.standardization;

import org.apache.skywalking.apm.collector.analysis.register.define.AnalysisRegisterModule;
import org.apache.skywalking.apm.collector.analysis.register.define.service.INetworkAddressIDService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IServiceNameService;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.SpanDecorator;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.table.register.ServerTypeDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class SpanIdExchanger implements IdExchanger<SpanDecorator> {

    private final Logger logger = LoggerFactory.getLogger(SpanIdExchanger.class);

    private static SpanIdExchanger EXCHANGER;
    private final IServiceNameService serviceNameService;
    private final INetworkAddressIDService networkAddressIDService;

    public static SpanIdExchanger getInstance(ModuleManager moduleManager) {
        if (EXCHANGER == null) {
            EXCHANGER = new SpanIdExchanger(moduleManager);
        }
        return EXCHANGER;
    }

    private SpanIdExchanger(ModuleManager moduleManager) {
        this.serviceNameService = moduleManager.find(AnalysisRegisterModule.NAME).getService(IServiceNameService.class);
        this.networkAddressIDService = moduleManager.find(AnalysisRegisterModule.NAME).getService(INetworkAddressIDService.class);
    }

    @Override public boolean exchange(SpanDecorator standardBuilder, int applicationId) {
        if (standardBuilder.getPeerId() == 0 && StringUtils.isNotEmpty(standardBuilder.getPeer())) {
            int peerId = networkAddressIDService.getOrCreate(standardBuilder.getPeer());

            if (peerId == 0) {
                logger.debug("peer: {} in application: {} exchange failed", standardBuilder.getPeer(), applicationId);
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setPeerId(peerId);
                standardBuilder.setPeer(Const.EMPTY_STRING);

                int spanLayer = standardBuilder.getSpanLayerValue();
                int serverType = ServerTypeDefine.getInstance().getServerTypeId(standardBuilder.getComponentId());
                networkAddressIDService.update(peerId, spanLayer, serverType);
            }
        }

        if (standardBuilder.getOperationNameId() == 0 && StringUtils.isNotEmpty(standardBuilder.getOperationName())) {
            int operationNameId = serviceNameService.getOrCreate(applicationId, standardBuilder.getSpanTypeValue(), standardBuilder.getOperationName());

            if (operationNameId == 0) {
                logger.debug("service name: {} from application id: {} exchange failed", standardBuilder.getOperationName(), applicationId);
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setOperationNameId(operationNameId);
                standardBuilder.setOperationName(Const.EMPTY_STRING);
            }
        }
        return true;
    }
}
