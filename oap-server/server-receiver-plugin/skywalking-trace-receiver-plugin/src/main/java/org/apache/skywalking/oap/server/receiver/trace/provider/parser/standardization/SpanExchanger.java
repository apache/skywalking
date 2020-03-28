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
import com.google.gson.JsonObject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.register.NodeType;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.register.service.INetworkAddressInventoryRegister;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.core.register.service.IServiceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SpanDecorator;

/**
 * SpanExchanger process the segment owner(service/instance) ID register, including operation name and network address.
 *
 * @since 6.6.0 only the operation name of entry span is registered as the endpoint, others keep the operation name as
 * the literal string.
 */
@Slf4j
public class SpanExchanger implements IdExchanger<SpanDecorator> {
    private static SpanExchanger EXCHANGER;
    private final ServiceInventoryCache serviceInventoryCacheDAO;
    private final IServiceInventoryRegister serviceInventoryRegister;
    private final ServiceInstanceInventoryCache serviceInstanceInventoryCacheDAO;
    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final INetworkAddressInventoryRegister networkAddressInventoryRegister;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    public static SpanExchanger getInstance(ModuleManager moduleManager) {
        if (EXCHANGER == null) {
            EXCHANGER = new SpanExchanger(moduleManager);
        }
        return EXCHANGER;
    }

    private SpanExchanger(ModuleManager moduleManager) {
        this.serviceInventoryCacheDAO = moduleManager.find(CoreModule.NAME)
                                                     .provider()
                                                     .getService(ServiceInventoryCache.class);
        this.serviceInventoryRegister = moduleManager.find(CoreModule.NAME)
                                                     .provider()
                                                     .getService(IServiceInventoryRegister.class);
        this.serviceInstanceInventoryCacheDAO = moduleManager.find(CoreModule.NAME)
                                                             .provider()
                                                             .getService(ServiceInstanceInventoryCache.class);
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME)
                                                             .provider()
                                                             .getService(IServiceInstanceInventoryRegister.class);
        this.networkAddressInventoryRegister = moduleManager.find(CoreModule.NAME)
                                                            .provider()
                                                            .getService(INetworkAddressInventoryRegister.class);
        this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME)
                                                           .provider()
                                                           .getService(IComponentLibraryCatalogService.class);
    }

    @Override
    public boolean exchange(SpanDecorator standardBuilder, int serviceId) {
        boolean exchanged = true;

        if (standardBuilder.getComponentId() == 0 && !Strings.isNullOrEmpty(standardBuilder.getComponent())) {
            int componentId = componentLibraryCatalogService.getComponentId(standardBuilder.getComponent());

            if (componentId == 0) {
                if (log.isDebugEnabled()) {
                    log.debug(
                        "component: {} in service: {} exchange failed", standardBuilder.getComponent(), serviceId);
                }

                exchanged = false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setComponentId(componentId);
                standardBuilder.setComponent(Const.EMPTY_STRING);
            }
        }

        int peerId = standardBuilder.getPeerId();
        if (peerId == 0 && !Strings.isNullOrEmpty(standardBuilder.getPeer())) {
            peerId = networkAddressInventoryRegister.getOrCreate(
                standardBuilder.getPeer(), buildServiceProperties(standardBuilder));

            if (peerId == Const.NONE) {
                if (log.isDebugEnabled()) {
                    log.debug("peer: {} in service: {} exchange failed", standardBuilder.getPeer(), serviceId);
                }

                exchanged = false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setPeerId(peerId);
                standardBuilder.setPeer(Const.EMPTY_STRING);
            }
        }

        if (peerId != Const.NONE) {
            int spanLayerValue = standardBuilder.getSpanLayerValue();
            NodeType nodeType = NodeType.fromSpanLayerValue(spanLayerValue);
            networkAddressInventoryRegister.update(peerId, nodeType);

            /*
             * In some case, conjecture node, such as Database node, could be registered by agents.
             * At here, if the target service properties need to be updated,
             * it will only be updated at the first time for now.
             */
            JsonObject properties = null;
            ServiceInventory newServiceInventory = serviceInventoryCacheDAO.get(
                serviceInventoryCacheDAO.getServiceId(peerId));
            if (SpanLayer.Database.equals(standardBuilder.getSpanLayer())) {
                if (!newServiceInventory.hasProperties()) {
                    properties = buildServiceProperties(standardBuilder);
                }
            }
            serviceInventoryRegister.update(newServiceInventory.getSequence(), nodeType, properties);

            ServiceInstanceInventory newServiceInstanceInventory = serviceInstanceInventoryCacheDAO.get(
                serviceInstanceInventoryCacheDAO
                    .getServiceInstanceId(newServiceInventory.getSequence(), peerId));
            serviceInstanceInventoryRegister.update(newServiceInstanceInventory.getSequence(), nodeType, properties);
        }

        return exchanged;
    }

    private JsonObject buildServiceProperties(SpanDecorator standardBuilder) {
        JsonObject properties = new JsonObject();
        if (SpanLayer.Database.equals(standardBuilder.getSpanLayer())) {
            List<KeyStringValuePair> tags = standardBuilder.getAllTags();
            tags.forEach(tag -> {
                if ("db.type".equals(tag.getKey())) {
                    properties.addProperty("type", tag.getValue());
                } else if ("db.instance".equals(tag.getKey())) {
                    properties.addProperty("instance", tag.getValue());
                }
            });
            String componentName;
            int id = standardBuilder.getComponentId();
            if (id != Const.NONE) {
                componentName = componentLibraryCatalogService.getServerNameBasedOnComponent(id);
            } else {
                componentName = "UNKNOWN";
            }
            properties.addProperty("database", componentName);
        }

        return properties;
    }
}
