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
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.register.service.INetworkAddressInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.ReferenceDecorator;

/**
 * Register the information inside the segment reference. All of them are downstream(caller) service information.
 * Reference could include multiple rows, as this span could have multiple downstream, such as batch process, typically
 * MQ consumer.
 *
 * Check the Cross Process Propagation Headers Protocol v2 for the details in the references.
 */
@Slf4j
public class ReferenceIdExchanger implements IdExchanger<ReferenceDecorator> {
    private static ReferenceIdExchanger EXCHANGER;
    private final ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private final INetworkAddressInventoryRegister networkAddressInventoryRegister;

    public static ReferenceIdExchanger getInstance(ModuleManager moduleManager) {
        if (EXCHANGER == null) {
            EXCHANGER = new ReferenceIdExchanger(moduleManager);
        }
        return EXCHANGER;
    }

    private ReferenceIdExchanger(ModuleManager moduleManager) {
        this.networkAddressInventoryRegister = moduleManager.find(CoreModule.NAME)
                                                            .provider()
                                                            .getService(INetworkAddressInventoryRegister.class);
        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME)
                                                          .provider()
                                                          .getService(ServiceInstanceInventoryCache.class);
    }

    /**
     * @since 7.1.0 Endpoint doesn't register anymore. Therefore, exchange of ref only relates to the network address only.
     */
    @Override
    public boolean exchange(ReferenceDecorator standardBuilder, int serviceId) {
        boolean exchanged = true;

        if (standardBuilder.getNetworkAddressId() == 0 && !Strings.isNullOrEmpty(standardBuilder.getNetworkAddress())) {
            int networkAddressId = networkAddressInventoryRegister.getOrCreate(
                standardBuilder.getNetworkAddress(), null);

            if (networkAddressId == 0) {
                if (log.isDebugEnabled()) {
                    log.debug(
                        "network getAddress: {} from service id: {} exchange failed",
                        standardBuilder.getNetworkAddress(), serviceId
                    );
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
}
