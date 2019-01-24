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
        boolean exchanged = true;

        if (standardBuilder.getEntryEndpointId() == 0) {
            String entryEndpointName = Strings.isNullOrEmpty(standardBuilder.getEntryEndpointName()) ? Const.DOMAIN_OPERATION_NAME : standardBuilder.getEntryEndpointName();
            int entryServiceId = serviceInstanceInventoryCache.get(standardBuilder.getEntryServiceInstanceId()).getServiceId();
            int entryEndpointId = getEndpointId(entryServiceId, entryEndpointName);
            if (entryEndpointId == 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("entry endpoint name: {} from service id: {} exchange failed", entryEndpointName, entryServiceId);
                }

                exchanged = false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setEntryEndpointId(entryEndpointId);
                standardBuilder.setEntryEndpointName(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getParentEndpointId() == 0) {
            String parentEndpointName = Strings.isNullOrEmpty(standardBuilder.getParentEndpointName()) ? Const.DOMAIN_OPERATION_NAME : standardBuilder.getParentEndpointName();
            int parentServiceId = serviceInstanceInventoryCache.get(standardBuilder.getParentServiceInstanceId()).getServiceId();
            int parentEndpointId = getEndpointId(parentServiceId, parentEndpointName);

            if (parentEndpointId == 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("parent endpoint name: {} from service id: {} exchange failed", parentEndpointName, parentServiceId);
                }

                exchanged = false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setParentEndpointId(parentEndpointId);
                standardBuilder.setParentEndpointName(Const.EMPTY_STRING);
            }
        }

        if (standardBuilder.getNetworkAddressId() == 0 && !Strings.isNullOrEmpty(standardBuilder.getNetworkAddress())) {
            int networkAddressId = networkAddressInventoryRegister.getOrCreate(standardBuilder.getNetworkAddress(), null);

            if (networkAddressId == 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("network getAddress: {} from service id: {} exchange failed", standardBuilder.getNetworkAddress(), serviceId);
                }

                exchanged = false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setNetworkAddressId(networkAddressId);
                standardBuilder.setNetworkAddress(Const.EMPTY_STRING);
            }
        }
        return exchanged;
    }

    /**
     * Endpoint in ref could be local or exit span's operation name.
     * Especially if it is local span operation name,
     * exchange may not happen at agent, such as Java agent,
     * then put literal endpoint string in the header,
     * Need to try to get the id by assuming the endpoint name is detected at server, local or client.
     *
     * If agent does the exchange, then always use endpoint id.
     *
     * @param serviceId
     * @param endpointName
     * @return
     */
    private int getEndpointId(int serviceId, String endpointName) {
        int endpointId = endpointInventoryRegister.get(serviceId, endpointName, DetectPoint.SERVER.ordinal());
        if (endpointId == Const.NONE) {
            endpointId = endpointInventoryRegister.get(serviceId, endpointName, DetectPoint.CLIENT.ordinal());
            if (endpointId == Const.NONE) {
                endpointId = endpointInventoryRegister.get(serviceId, endpointName, DetectPoint.UNRECOGNIZED.ordinal());
            }
        }
        return endpointId;
    }
}
