package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.noderef.NodeReferenceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
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

        for (int i = 0; i < resSumArray.size(); i++) {
            JsonObject nodeRefJsonObj = resSumArray.get(i).getAsJsonObject();
            String front = nodeRefJsonObj.get("front").getAsString();
            String behind = nodeRefJsonObj.get("behind").getAsString();

            if (hasMapping(behind)) {
                continue;
            }

            JsonObject lineJsonObj = new JsonObject();
            lineJsonObj.addProperty("from", findOrCreateNode(front));
            lineJsonObj.addProperty("to", findOrCreateNode(behind));
            lineJsonObj.addProperty("resSum", nodeRefJsonObj.get(NodeReferenceTable.COLUMN_SUMMARY).getAsInt());

            lineArray.add(lineJsonObj);
            logger.debug("line: {}", lineJsonObj);
        }

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
}
