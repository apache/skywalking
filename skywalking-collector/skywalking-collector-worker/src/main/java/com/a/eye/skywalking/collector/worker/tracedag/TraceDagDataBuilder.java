package com.a.eye.skywalking.collector.worker.tracedag;

import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.node.NodeCompIndex;
import com.a.eye.skywalking.collector.worker.node.NodeMappingIndex;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class TraceDagDataBuilder {

    private Logger logger = LogManager.getFormatterLogger(TraceDagDataBuilder.class);

    private Integer nodeId = new Integer(-1);
    private Map<String, String> mappingMap = new HashMap<>();
    private Map<String, String> nodeCompMap = new HashMap<>();
    private Map<String, Long> resSumMap = new HashMap<>();
    private Map<String, Integer> nodeIdMap = new HashMap<>();
    private JsonArray pointArray = new JsonArray();
    private JsonArray lineArray = new JsonArray();

    public JsonObject build(JsonArray nodeCompArray, JsonArray nodesMappingArray, JsonArray nodeRefsArray,
        JsonArray resSumArray) {
        changeMapping2Map(nodesMappingArray);
        changeNodeComp2Map(nodeCompArray);
        resSumMerge(resSumArray);

        for (int i = 0; i < nodeRefsArray.size(); i++) {
            JsonObject nodeRefJsonObj = nodeRefsArray.get(i).getAsJsonObject();
            String front = nodeRefJsonObj.get(NodeRefIndex.FRONT).getAsString();
            String behind = nodeRefJsonObj.get(NodeRefIndex.BEHIND).getAsString();

            String behindCode = findRealCode(behind);
            logger.debug("behind: %s, behindCode: %s", behind, behindCode);

            JsonObject lineJsonObj = new JsonObject();
            lineJsonObj.addProperty("from", findOrCreateNode(front));
            lineJsonObj.addProperty("to", findOrCreateNode(behindCode));
            lineJsonObj.addProperty("resSum", resSumMap.get(front + Const.ID_SPLIT + behindCode));

            lineArray.add(lineJsonObj);
            logger.debug("line: %s", lineJsonObj);
        }

        JsonObject dagJsonObj = new JsonObject();
        dagJsonObj.add("NODES", pointArray);
        dagJsonObj.add("nodeRefs", lineArray);
        return dagJsonObj;
    }

    private Integer findOrCreateNode(String peers) {
        if (nodeIdMap.containsKey(peers)) {
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
            logger.debug("node: %s", nodeJsonObj);
        }
        return nodeId;
    }

    private void changeMapping2Map(JsonArray nodesMappingArray) {
        for (int i = 0; i < nodesMappingArray.size(); i++) {
            JsonObject nodesMappingJsonObj = nodesMappingArray.get(i).getAsJsonObject();
            String code = nodesMappingJsonObj.get(NodeMappingIndex.CODE).getAsString();
            String peers = nodesMappingJsonObj.get(NodeMappingIndex.PEERS).getAsString();
            mappingMap.put(peers, code);
        }
    }

    private void changeNodeComp2Map(JsonArray nodeCompArray) {
        for (int i = 0; i < nodeCompArray.size(); i++) {
            JsonObject nodesJsonObj = nodeCompArray.get(i).getAsJsonObject();
            logger.debug(nodesJsonObj);
            String component = nodesJsonObj.get(NodeCompIndex.NAME).getAsString();
            String peers = nodesJsonObj.get(NodeCompIndex.PEERS).getAsString();
            nodeCompMap.put(peers, component);
        }
    }

    private String findRealCode(String peers) {
        if (mappingMap.containsKey(peers)) {
            return mappingMap.get(peers);
        } else {
            return peers;
        }
    }

    private void resSumMerge(JsonArray resSumArray) {
        for (int i = 0; i < resSumArray.size(); i++) {
            JsonObject resSumJsonObj = resSumArray.get(i).getAsJsonObject();
            String front = resSumJsonObj.get("front").getAsString();
            String behind = resSumJsonObj.get("behind").getAsString();
            Long summary = resSumJsonObj.get("summary").getAsLong();

            if (mappingMap.containsKey(behind)) {
                behind = mappingMap.get(behind);
            }

            String id = front + Const.ID_SPLIT + behind;
            if (resSumMap.containsKey(id)) {
                resSumMap.put(id, summary + resSumMap.get(id));
            } else {
                resSumMap.put(id, summary);
            }
        }
    }
}
