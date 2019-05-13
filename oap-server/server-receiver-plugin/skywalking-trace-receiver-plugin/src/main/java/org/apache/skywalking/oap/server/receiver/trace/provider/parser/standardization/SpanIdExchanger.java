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
import org.apache.skywalking.apm.network.common.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.register.service.*;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator.SpanDecorator;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class SpanIdExchanger implements IdExchanger<SpanDecorator> {

    private static final Logger logger = LoggerFactory.getLogger(SpanIdExchanger.class);

    private static SpanIdExchanger EXCHANGER;
    private final ServiceInventoryCache serviceInventoryCacheDAO;
    private final IServiceInventoryRegister serviceInventoryRegister;
    private final IEndpointInventoryRegister endpointInventoryRegister;
    private final INetworkAddressInventoryRegister networkAddressInventoryRegister;
    private final IComponentLibraryCatalogService componentLibraryCatalogService;

    public static SpanIdExchanger getInstance(ModuleManager moduleManager) {
        if (EXCHANGER == null) {
            EXCHANGER = new SpanIdExchanger(moduleManager);
        }
        return EXCHANGER;
    }

    private SpanIdExchanger(ModuleManager moduleManager) {
        this.serviceInventoryCacheDAO = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        this.serviceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
        this.endpointInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IEndpointInventoryRegister.class);
        this.networkAddressInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(INetworkAddressInventoryRegister.class);
        this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME).provider().getService(IComponentLibraryCatalogService.class);
    }

    @Override public boolean exchange(SpanDecorator standardBuilder, int serviceId) {
        boolean exchanged = true;

        if (standardBuilder.getComponentId() == 0 && !Strings.isNullOrEmpty(standardBuilder.getComponent())) {
            int componentId = componentLibraryCatalogService.getComponentId(standardBuilder.getComponent());

            if (componentId == 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("component: {} in service: {} exchange failed", standardBuilder.getComponent(), serviceId);
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
            peerId = networkAddressInventoryRegister.getOrCreate(standardBuilder.getPeer(), buildServiceProperties(standardBuilder));

            if (peerId == Const.NONE) {
                if (logger.isDebugEnabled()) {
                    logger.debug("peer: {} in service: {} exchange failed", standardBuilder.getPeer(), serviceId);
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

            /**
             * In some case, conjecture node, such as Database node, could be registered by agents.
             * At here, if the target service properties need to be updated,
             * it will only be updated at the first time for now.
             */

            JsonObject properties = null;
            ServiceInventory newServiceInventory = serviceInventoryCacheDAO.get(serviceInventoryCacheDAO.getServiceId(peerId));
            if (SpanLayer.Database.equals(standardBuilder.getSpanLayer())) {
                if (!newServiceInventory.hasProperties()) {
                    properties = buildServiceProperties(standardBuilder);
                }
            }
            serviceInventoryRegister.update(newServiceInventory.getSequence(), nodeType, properties);
        }

        if (standardBuilder.getOperationNameId() == Const.NONE) {
            String endpointName = Strings.isNullOrEmpty(standardBuilder.getOperationName()) ? Const.DOMAIN_OPERATION_NAME : standardBuilder.getOperationName();
            int endpointId = endpointInventoryRegister.getOrCreate(serviceId, endpointName, DetectPoint.fromSpanType(standardBuilder.getSpanType()));

            if (endpointId == 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("endpoint name: {} from service id: {} exchange failed", endpointName, serviceId);
                }

                exchanged = false;
            } else {
                standardBuilder.toBuilder();
                standardBuilder.setOperationNameId(endpointId);
                standardBuilder.setOperationName(Const.EMPTY_STRING);
            }
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
