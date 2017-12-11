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


package org.apache.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMappingTable;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TraceDagDataBuilder {
    private final Logger logger = LoggerFactory.getLogger(TraceDagDataBuilder.class);

    private Integer nodeId = -1;
    private Map<String, String> mappingMap = new HashMap<>();
    private Map<String, String> nodeCompMap = new HashMap<>();
    private Map<String, Integer> nodeIdMap = new HashMap<>();
    private JsonArray pointArray = new JsonArray();
    private JsonArray lineArray = new JsonArray();
    private final ApplicationCacheService applicationCacheService;

    public TraceDagDataBuilder(ModuleManager moduleManager) {
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    public JsonObject build(JsonArray nodeCompArray, JsonArray nodesMappingArray, JsonArray resSumArray) {
        changeNodeComp2Map(nodeCompArray);
        changeMapping2Map(nodesMappingArray);

        Map<String, JsonObject> mergedResSumMap = getApplicationCode(resSumArray);

        mergedResSumMap.values().forEach(nodeRefJsonObj -> {
            String front = nodeRefJsonObj.get("front").getAsString();
            String behind = nodeRefJsonObj.get("behind").getAsString();

            if (hasMapping(behind)) {
                return;
            }

            JsonObject lineJsonObj = new JsonObject();
            lineJsonObj.addProperty("from", findOrCreateNode(front));
            lineJsonObj.addProperty("to", findOrCreateNode(behind));
            lineJsonObj.addProperty("resSum", nodeRefJsonObj.get(ApplicationReferenceMetricTable.COLUMN_TRANSACTION_CALLS).getAsInt());

            lineArray.add(lineJsonObj);
            logger.debug("line: {}", lineJsonObj);
        });

        JsonObject dagJsonObj = new JsonObject();
        dagJsonObj.add("nodes", pointArray);
        dagJsonObj.add("nodeRefs", lineArray);
        return dagJsonObj;
    }

    private Integer findOrCreateNode(String peers) {
        if (nodeIdMap.containsKey(peers) && !peers.equals(Const.USER_CODE)) {
            return nodeIdMap.get(peers);
        } else {
            nodeId++;
            JsonObject nodeJsonObj = new JsonObject();
            nodeJsonObj.addProperty("id", nodeId);
            nodeJsonObj.addProperty("peer", peers);
            if (peers.equals(Const.USER_CODE)) {
                nodeJsonObj.addProperty("component", Const.USER_CODE);
            } else {
                nodeJsonObj.addProperty("component", nodeCompMap.get(peers));
            }
            pointArray.add(nodeJsonObj);

            nodeIdMap.put(peers, nodeId);
            logger.debug("node: {}", nodeJsonObj);
        }
        return nodeId;
    }

    private void changeMapping2Map(JsonArray nodesMappingArray) {
        for (int i = 0; i < nodesMappingArray.size(); i++) {
            JsonObject nodesMappingJsonObj = nodesMappingArray.get(i).getAsJsonObject();
            int applicationId = nodesMappingJsonObj.get(ApplicationMappingTable.COLUMN_APPLICATION_ID).getAsInt();
            String applicationCode = applicationCacheService.get(applicationId);
            int addressId = nodesMappingJsonObj.get(ApplicationMappingTable.COLUMN_ADDRESS_ID).getAsInt();
            String address = applicationCacheService.get(addressId);
            mappingMap.put(address, applicationCode);
        }
    }

    private void changeNodeComp2Map(JsonArray nodeCompArray) {
        for (int i = 0; i < nodeCompArray.size(); i++) {
            JsonObject nodesJsonObj = nodeCompArray.get(i).getAsJsonObject();
            logger.debug(nodesJsonObj.toString());
            int componentId = nodesJsonObj.get(ApplicationComponentTable.COLUMN_COMPONENT_ID).getAsInt();
            String componentName = ComponentsDefine.getInstance().getComponentName(componentId);
            int peerId = nodesJsonObj.get(ApplicationComponentTable.COLUMN_PEER_ID).getAsInt();
            String peer = applicationCacheService.get(peerId);
            nodeCompMap.put(peer, componentName);
        }
    }

    private boolean hasMapping(String peers) {
        return mappingMap.containsKey(peers);
    }

    private Map<String, JsonObject> getApplicationCode(JsonArray nodeReference) {
        Map<String, JsonObject> mergedRef = new LinkedHashMap<>();
        for (int i = 0; i < nodeReference.size(); i++) {
            JsonObject nodeRefJsonObj = nodeReference.get(i).getAsJsonObject();

            int frontApplicationId = nodeRefJsonObj.get(ColumnNameUtils.INSTANCE.rename(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID)).getAsInt();
            int behindApplicationId = nodeRefJsonObj.get(ColumnNameUtils.INSTANCE.rename(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID)).getAsInt();

            String front = applicationCacheService.get(frontApplicationId);
            String behind = applicationCacheService.get(behindApplicationId);

            String id = front + Const.ID_SPLIT + behind;
            nodeRefJsonObj.addProperty("front", front);
            nodeRefJsonObj.addProperty("behind", behind);
            mergedRef.put(id, nodeRefJsonObj);
        }

        return mergedRef;
    }
}
