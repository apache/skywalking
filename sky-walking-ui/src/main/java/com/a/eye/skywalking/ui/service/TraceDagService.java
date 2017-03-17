package com.a.eye.skywalking.ui.service;

import com.a.eye.skywalking.ui.config.ImageBase64Config;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author pengys5
 */
@Service
public class TraceDagService {

    @Autowired
    private ImageBase64Config config;

    public JsonObject getDag() {
        JsonObject dagJson = new JsonObject();
        dagJson.add("nodes", getNodes());
        dagJson.add("nodeRefs", getNodeRefs());
        return dagJson;
    }

    public JsonArray getNodes() {
        JsonArray nodes = new JsonArray();
        nodes.add(createNode(1, "User", config.getImage().get("User"), 1));
        nodes.add(createNode(2, "Front-Web", config.getImage().get("Tomcat"), 15));
        nodes.add(createNode(3, "Backend-API", config.getImage().get("Tomcat"), 4));
        nodes.add(createNode(4, "Backend-Web", config.getImage().get("Tomcat"), 3));
        nodes.add(createNode(5, "User", config.getImage().get("User"), 2));
        nodes.add(createNode(6, "MemCached", config.getImage().get("Oracle"), 1));
        nodes.add(createNode(7, "UNKNOWN_CLOUD", config.getImage().get("Mysql"), 1));
        nodes.add(createNode(8, "MYSQL", config.getImage().get("Mysql"), 1));
        nodes.add(createNode(9, "ORACLE", config.getImage().get("Oracle"), 2));
        return nodes;
    }

    public JsonArray getNodeRefs() {
        JsonArray nodeRefs = new JsonArray();
        nodeRefs.add(createNodeRef(1, 2, 100));
        nodeRefs.add(createNodeRef(2, 3, 100));
        nodeRefs.add(createNodeRef(2, 4, 100));
        nodeRefs.add(createNodeRef(4, 3, 100));
        nodeRefs.add(createNodeRef(2, 6, 100));
        nodeRefs.add(createNodeRef(2, 7, 100));
        nodeRefs.add(createNodeRef(5, 4, 100));
        nodeRefs.add(createNodeRef(3, 8, 100));
        nodeRefs.add(createNodeRef(3, 9, 100));
        return nodeRefs;
    }

    public JsonObject createNode(int id, String label, String image, int instNum) {
        JsonObject nodeJsonObj = new JsonObject();
        nodeJsonObj.addProperty("id", id);
        nodeJsonObj.addProperty("label", label);
        nodeJsonObj.addProperty("image", image);
        nodeJsonObj.addProperty("instNum", instNum);
        return nodeJsonObj;
    }

    public JsonObject createNodeRef(int from, int to, int resSum) {
        JsonObject nodeRefJsonObj = new JsonObject();
        nodeRefJsonObj.addProperty("from", from);
        nodeRefJsonObj.addProperty("to", to);
        nodeRefJsonObj.addProperty("resSum", resSum);
        return nodeRefJsonObj;
    }
}
