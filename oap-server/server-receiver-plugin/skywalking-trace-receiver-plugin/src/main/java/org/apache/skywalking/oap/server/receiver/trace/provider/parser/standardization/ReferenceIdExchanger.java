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

import com.google.common.base.Strings;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.register.service.*;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
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
        this.endpointInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IEndpointInventoryRegister.class);
        this.networkAddressInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(INetworkAddressInventoryRegister.class);
        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class);
    }

    @Override public boolean exchange(ReferenceDecorator standardBuilder, int serviceId) {
        if (standardBuilder.getEntryEndpointId() == 0) {
            String entryEndpointName = Strings.isNullOrEmpty(standardBuilder.getEntryEndpointName()) ? Const.DOMAIN_OPERATION_NAME : standardBuilder.getEntryEndpointName();
            int entryEndpointId = endpointInventoryRegister.get(serviceInstanceInventoryCache.get(standardBuilder.getEntryServiceInstanceId()).getServiceId(), entryEndpointName, DetectPoint.SERVER.ordinal());

            if (entryEndpointId == 0) {
                if (logger.isDebugEnabled()) {
                    int entryServiceId = serviceInstanceInventoryCache.get(standardBuilder.getEntryServiceInstanceId()).getServiceId();
                    logger.debug("entry endpoint name: {} from service id: {} exchange failed", entryEndpointName, entryServiceId);
                }
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setEntryEndpointId(entryEndpointId);
                standardBuilder.setEntryEndpointName(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getParentEndpointId() == 0) {
            String parentEndpointName = Strings.isNullOrEmpty(standardBuilder.getParentEndpointName()) ? Const.DOMAIN_OPERATION_NAME : standardBuilder.getParentEndpointName();
            int parentEndpointId = endpointInventoryRegister.get(serviceInstanceInventoryCache.get(standardBuilder.getParentServiceInstanceId()).getServiceId(), parentEndpointName, DetectPoint.SERVER.ordinal());

            if (parentEndpointId == 0) {
                if (logger.isDebugEnabled()) {
                    int parentServiceId = serviceInstanceInventoryCache.get(standardBuilder.getParentServiceInstanceId()).getServiceId();
                    logger.debug("parent endpoint name: {} from service id: {} exchange failed", parentEndpointName, parentServiceId);
                }
                return false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setParentEndpointId(parentEndpointId);
                standardBuilder.setParentEndpointName(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getNetworkAddressId() == 0 && !Strings.isNullOrEmpty(standardBuilder.getNetworkAddress())) {
            int networkAddressId = networkAddressInventoryRegister.getOrCreate(standardBuilder.getNetworkAddress());

            if (networkAddressId == 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("network getAddress: {} from service id: {} exchange failed", standardBuilder.getNetworkAddress(), serviceId);
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
