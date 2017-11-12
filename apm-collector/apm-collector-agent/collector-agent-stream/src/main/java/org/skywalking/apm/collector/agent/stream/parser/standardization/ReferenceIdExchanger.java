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

package org.skywalking.apm.collector.agent.stream.parser.standardization;

import org.skywalking.apm.collector.agent.stream.worker.register.ServiceNameService;
import org.skywalking.apm.collector.cache.CacheServiceManager;
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
    private final CacheServiceManager cacheServiceManager;

    public static ReferenceIdExchanger getInstance(CacheServiceManager cacheServiceManager) {
        if (EXCHANGER == null) {
            EXCHANGER = new ReferenceIdExchanger(cacheServiceManager);
        }
        return EXCHANGER;
    }

    public ReferenceIdExchanger(CacheServiceManager cacheServiceManager) {
        this.cacheServiceManager = cacheServiceManager;
        serviceNameService = new ServiceNameService(cacheServiceManager);
    }

    @Override public boolean exchange(ReferenceDecorator standardBuilder, int applicationId) {
        if (standardBuilder.getEntryServiceId() == 0 && StringUtils.isNotEmpty(standardBuilder.getEntryServiceName())) {
            int entryServiceId = serviceNameService.getOrCreate(cacheServiceManager.getInstanceCacheService().get(standardBuilder.getEntryApplicationInstanceId()), standardBuilder.getEntryServiceName());
            if (entryServiceId == 0) {
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setEntryServiceId(entryServiceId);
                standardBuilder.setEntryServiceName(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getParentServiceId() == 0 && StringUtils.isNotEmpty(standardBuilder.getParentServiceName())) {
            int parentServiceId = serviceNameService.getOrCreate(cacheServiceManager.getInstanceCacheService().get(standardBuilder.getParentApplicationInstanceId()), standardBuilder.getParentServiceName());
            if (parentServiceId == 0) {
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setParentServiceId(parentServiceId);
                standardBuilder.setParentServiceName(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getNetworkAddressId() == 0 && StringUtils.isNotEmpty(standardBuilder.getNetworkAddress())) {
            int networkAddressId = cacheServiceManager.getApplicationCacheService().get(standardBuilder.getNetworkAddress());
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
