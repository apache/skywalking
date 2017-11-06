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

package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.noderef.NodeReferenceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class TraceDagDataBuilder {
    private final Logger logger = LoggerFactory.getLogger(TraceDagDataBuilder.class);

    private Integer nodeId = new Integer(-1);
    private Map<String, String> mappingMap = new HashMap<>();
    private Map<String, String> nodeCompMap = new HashMap<>();
    private Map<String, Integer> nodeIdMap = new HashMap<>();
    private JsonArray pointArray = new JsonArray();
    private JsonArray lineArray = new JsonArray();

    public JsonObject build(JsonArray nodeCompArray, JsonArray nodesMappingArray, JsonArray resSumArray) {
        changeNodeComp2Map(nodeCompArray);
        changeMapping2Map(nodesMappingArray);

        Map<String, JsonObject> mergedResSumMap = merge(resSumArray);

        mergedResSumMap.values().forEach(nodeRefJsonObj -> {
            String front = nodeRefJsonObj.get("front").getAsString();
            String behind = nodeRefJsonObj.get("behind").getAsString();

            if (hasMapping(behind)) {
                return;
            }

            JsonObject lineJsonObj = new JsonObject();
            lineJsonObj.addProperty("from", findOrCreateNode(front));
            lineJsonObj.addProperty("to", findOrCreateNode(behind));
            lineJsonObj.addProperty("resSum", nodeRefJsonObj.get(NodeReferenceTable.COLUMN_SUMMARY).getAsInt());

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
            String applicationCode = nodesMappingJsonObj.get("applicationCode").getAsString();
            String address = nodesMappingJsonObj.get("address").getAsString();
            mappingMap.put(address, applicationCode);
        }
    }

    private void changeNodeComp2Map(JsonArray nodeCompArray) {
        for (int i = 0; i < nodeCompArray.size(); i++) {
            JsonObject nodesJsonObj = nodeCompArray.get(i).getAsJsonObject();
            logger.debug(nodesJsonObj.toString());
            String componentName = nodesJsonObj.get("componentName").getAsString();
            String peer = nodesJsonObj.get("peer").getAsString();
            nodeCompMap.put(peer, componentName);
        }
    }

    private boolean hasMapping(String peers) {
        return mappingMap.containsKey(peers);
    }

    private Map<String, JsonObject> merge(JsonArray nodeReference) {
        Map<String, JsonObject> mergedRef = new LinkedHashMap<>();
        for (int i = 0; i < nodeReference.size(); i++) {
            JsonObject nodeRefJsonObj = nodeReference.get(i).getAsJsonObject();
            String front = nodeRefJsonObj.get("front").getAsString();
            String behind = nodeRefJsonObj.get("behind").getAsString();

            String id = front + Const.ID_SPLIT + behind;
            if (mergedRef.containsKey(id)) {
                JsonObject oldValue = mergedRef.get(id);
                oldValue.addProperty(NodeReferenceTable.COLUMN_S1_LTE, oldValue.get(NodeReferenceTable.COLUMN_S1_LTE).getAsLong() + nodeRefJsonObj.get(NodeReferenceTable.COLUMN_S1_LTE).getAsLong());
                oldValue.addProperty(NodeReferenceTable.COLUMN_S3_LTE, oldValue.get(NodeReferenceTable.COLUMN_S3_LTE).getAsLong() + nodeRefJsonObj.get(NodeReferenceTable.COLUMN_S3_LTE).getAsLong());
                oldValue.addProperty(NodeReferenceTable.COLUMN_S5_LTE, oldValue.get(NodeReferenceTable.COLUMN_S5_LTE).getAsLong() + nodeRefJsonObj.get(NodeReferenceTable.COLUMN_S5_LTE).getAsLong());
                oldValue.addProperty(NodeReferenceTable.COLUMN_S5_GT, oldValue.get(NodeReferenceTable.COLUMN_S5_GT).getAsLong() + nodeRefJsonObj.get(NodeReferenceTable.COLUMN_S5_GT).getAsLong());
                oldValue.addProperty(NodeReferenceTable.COLUMN_ERROR, oldValue.get(NodeReferenceTable.COLUMN_ERROR).getAsLong() + nodeRefJsonObj.get(NodeReferenceTable.COLUMN_ERROR).getAsLong());
                oldValue.addProperty(NodeReferenceTable.COLUMN_SUMMARY, oldValue.get(NodeReferenceTable.COLUMN_SUMMARY).getAsLong() + nodeRefJsonObj.get(NodeReferenceTable.COLUMN_SUMMARY).getAsLong());
            } else {
                mergedRef.put(id, nodeReference.get(i).getAsJsonObject());
            }
        }

        return mergedRef;
    }
}
