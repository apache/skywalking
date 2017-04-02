package com.a.eye.skywalking.ui.service;

import com.a.eye.skywalking.ui.creator.ImageCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
@Service
public class TraceDagGraphBuildService {

    private Logger logger = LogManager.getFormatterLogger(TraceDagGraphBuildService.class);

    @Autowired
    private ImageCache imageCache;

    public JsonObject buildNodesGraph(JsonArray nodesArray, JsonArray nodeRefArray, Map<String, JsonObject> nodeRefResSumMapping) {
        JsonObject graphDataJson = new JsonObject();
        logger.debug("rename node ref: %s", nodeRefArray.toString());

        Map<String, JsonObject> nodeDataMap = new HashMap<>();
        for (int i = 0; i < nodesArray.size(); i++) {
            JsonObject nodeJsonObj = nodesArray.get(i).getAsJsonObject();
            String code = nodeJsonObj.get("code").getAsString();
            logger.debug("node %s, data: %s", code, nodeJsonObj.toString());
            nodeDataMap.put(code, nodeJsonObj);
        }

        int nodeId = 0;
        Map<String, Integer> nodeMapping = new HashMap<>();

        JsonArray graphNodeRef = new JsonArray();
        JsonArray graphNodes = new JsonArray();
        for (int i = 0; i < nodeRefArray.size(); i++) {
            JsonObject nodeRefJsonObj = nodeRefArray.get(i).getAsJsonObject();
            String behind = nodeRefJsonObj.get("behind").getAsString();
            String front = nodeRefJsonObj.get("front").getAsString();

            logger.debug("front node code: %s, behind node code: %s", front, behind);
            if (!nodeMapping.containsKey(front)) {
                String component = ImageCache.UNDEFINED_IMAGE;
                if (nodeDataMap.get(front).has("component")) {
                    component = nodeDataMap.get(front).get("component").getAsString();
                }

                int nodeInstSum = 0;
//                if (sumMapping.containsKey(front)) {
//                    nodeInstSum = sumMapping.get(front);
//                }
                graphNodes.add(createNodeGraph(nodeId, front, imageCache.getImage(component), nodeInstSum));
                nodeMapping.put(front, nodeId);
                nodeId++;
            }

            if (!nodeMapping.containsKey(behind)) {
                logger.debug("behind: %s", behind);

                String component = ImageCache.UNDEFINED_IMAGE;
                if (nodeDataMap.get(front).has("component")) {
                    component = nodeDataMap.get(behind).get("component").getAsString();
                }

                int nodeInstSum = 0;
//                if (sumMapping.containsKey(behind)) {
//                    nodeInstSum = sumMapping.get(behind);
//                }

                graphNodes.add(createNodeGraph(nodeId, behind, imageCache.getImage(component), nodeInstSum));
                nodeMapping.put(behind, nodeId);
                nodeId++;
            }

            int nodeRefResSum = 0;
            if (nodeRefResSumMapping.containsKey(front + "-" + behind)) {
                nodeRefResSum = nodeRefResSumMapping.get(front + "-" + behind).get("summary").getAsInt();
            }

            graphNodeRef.add(createNodeRefGraph(nodeMapping.get(front), nodeMapping.get(behind), nodeRefResSum));
        }
        graphDataJson.add("nodes", graphNodes);
        graphDataJson.add("nodeRefs", graphNodeRef);
        return graphDataJson;
    }

    private JsonObject createNodeGraph(int id, String label, String image, int instNum) {
        JsonObject nodeJsonObj = new JsonObject();
        nodeJsonObj.addProperty("id", id);
        nodeJsonObj.addProperty("label", label);
        nodeJsonObj.addProperty("image", image);
        nodeJsonObj.addProperty("instNum", instNum);
        return nodeJsonObj;
    }

    private JsonObject createNodeRefGraph(int from, int to, int resSum) {
        JsonObject nodeRefJsonObj = new JsonObject();
        nodeRefJsonObj.addProperty("from", from);
        nodeRefJsonObj.addProperty("to", to);
        nodeRefJsonObj.addProperty("resSum", resSum);
        return nodeRefJsonObj;
    }
}
