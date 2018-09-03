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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.standardization;

import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.register.service.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtils;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.ReferenceDecorator;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ReferenceIdExchanger implements IdExchanger<ReferenceDecorator> {

    private static final Logger logger = LoggerFactory.getLogger(ReferenceIdExchanger.class);

    private static ReferenceIdExchanger EXCHANGER;
    private final IEndpointInventoryRegister endpointInventoryRegister;
    private final ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private final INetworkAddressInventoryRegister networkAddressInventoryRegister;

    public static ReferenceIdExchanger getInstance(ModuleManager moduleManager) {
        if (EXCHANGER == null) {
            EXCHANGER = new ReferenceIdExchanger(moduleManager);
        }
        return EXCHANGER;
    }

    private ReferenceIdExchanger(ModuleManager moduleManager) {
        this.endpointInventoryRegister = moduleManager.find(CoreModule.NAME).getService(IEndpointInventoryRegister.class);
        this.networkAddressInventoryRegister = moduleManager.find(CoreModule.NAME).getService(INetworkAddressInventoryRegister.class);
        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME).getService(ServiceInstanceInventoryCache.class);
    }

    @Override public boolean exchange(ReferenceDecorator standardBuilder, int serviceId) {
        if (standardBuilder.getEntryServiceId() == 0) {
            String entryEndpointName = StringUtils.isNotEmpty(standardBuilder.getEntryServiceName()) ? standardBuilder.getEntryServiceName() : Const.DOMAIN_OPERATION_NAME;
            int entryEndpointId = endpointInventoryRegister.get(serviceInstanceInventoryCache.getServiceId(standardBuilder.getEntryApplicationInstanceId()), entryEndpointName);

            if (entryEndpointId == 0) {
                if (logger.isDebugEnabled()) {
                    int entryServiceId = serviceInstanceInventoryCache.getServiceId(standardBuilder.getEntryApplicationInstanceId());
                    logger.debug("entry endpoint name: {} from service id: {} exchange failed", entryEndpointName, entryServiceId);
                }
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setEntryServiceId(entryEndpointId);
                standardBuilder.setEntryServiceName(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getParentServiceId() == 0) {
            String parentEndpointName = StringUtils.isNotEmpty(standardBuilder.getParentServiceName()) ? standardBuilder.getParentServiceName() : Const.DOMAIN_OPERATION_NAME;
            int parentEndpointId = endpointInventoryRegister.get(serviceInstanceInventoryCache.getServiceId(standardBuilder.getParentApplicationInstanceId()), parentEndpointName);

            if (parentEndpointId == 0) {
                if (logger.isDebugEnabled()) {
                    int parentServiceId = serviceInstanceInventoryCache.getServiceId(standardBuilder.getParentApplicationInstanceId());
                    logger.debug("parent endpoint name: {} from service id: {} exchange failed", parentEndpointName, parentServiceId);
                }
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setParentServiceId(parentEndpointId);
                standardBuilder.setParentServiceName(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getNetworkAddressId() == 0 && StringUtils.isNotEmpty(standardBuilder.getNetworkAddress())) {
            int networkAddressId = networkAddressInventoryRegister.getOrCreate(standardBuilder.getNetworkAddress());

            if (networkAddressId == 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("network address: {} from service id: {} exchange failed", standardBuilder.getNetworkAddress(), serviceId);
                }
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
