package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.util.Const;
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
            String front = nodeRefJsonObj.get("front").getAsString();
            String behind = nodeRefJsonObj.get("behind").getAsString();

            String behindCode = findRealCode(behind);
            logger.debug("behind: %s, behindCode: {}", behind, behindCode);

            JsonObject lineJsonObj = new JsonObject();
            lineJsonObj.addProperty("from", findOrCreateNode(front));
            lineJsonObj.addProperty("to", findOrCreateNode(behindCode));
            lineJsonObj.addProperty("resSum", resSumMap.get(front + Const.ID_SPLIT + behindCode));

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
            String code = nodesMappingJsonObj.get("code").getAsString();
            String peers = nodesMappingJsonObj.get("peers").getAsString();
            mappingMap.put(peers, code);
        }
    }

    private void changeNodeComp2Map(JsonArray nodeCompArray) {
        for (int i = 0; i < nodeCompArray.size(); i++) {
            JsonObject nodesJsonObj = nodeCompArray.get(i).getAsJsonObject();
            logger.debug(nodesJsonObj.toString());
            String component = nodesJsonObj.get("name").getAsString();
            String peers = nodesJsonObj.get("peers").getAsString();
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
