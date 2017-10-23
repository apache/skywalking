/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream.worker.segment.standardization;

import org.skywalking.apm.collector.agentregister.servicename.ServiceNameService;
import org.skywalking.apm.collector.cache.ApplicationCache;
import org.skywalking.apm.collector.cache.InstanceCache;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ReferenceIdExchanger implements IdExchanger<ReferenceDecorator> {

    private final Logger logger = LoggerFactory.getLogger(ReferenceIdExchanger.class);

    private static ReferenceIdExchanger EXCHANGER;
    private ServiceNameService serviceNameService;

    public static ReferenceIdExchanger getInstance() {
        if (EXCHANGER == null) {
            EXCHANGER = new ReferenceIdExchanger();
        }
        return EXCHANGER;
    }

    public ReferenceIdExchanger() {
        serviceNameService = new ServiceNameService();
    }

    @Override public boolean exchange(ReferenceDecorator standardBuilder, int applicationId) {
        if (standardBuilder.getEntryServiceId() == 0 && StringUtils.isNotEmpty(standardBuilder.getEntryServiceName())) {
            int entryServiceId = serviceNameService.getOrCreate(InstanceCache.get(standardBuilder.getEntryApplicationInstanceId()), standardBuilder.getEntryServiceName());
            if (entryServiceId == 0) {
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setEntryServiceId(entryServiceId);
                standardBuilder.setEntryServiceName(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getParentServiceId() == 0 && StringUtils.isNotEmpty(standardBuilder.getParentServiceName())) {
            int parentServiceId = serviceNameService.getOrCreate(InstanceCache.get(standardBuilder.getParentApplicationInstanceId()), standardBuilder.getParentServiceName());
            if (parentServiceId == 0) {
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setParentServiceId(parentServiceId);
                standardBuilder.setParentServiceName(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getNetworkAddressId() == 0 && StringUtils.isNotEmpty(standardBuilder.getNetworkAddress())) {
            int networkAddressId = ApplicationCache.get(standardBuilder.getNetworkAddress());
            if (networkAddressId == 0) {
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setNetworkAddressId(networkAddressId);
                standardBuilder.setNetworkAddress(Const.EMPTY_STRING);
            }
        }
        return true;
    }
}
