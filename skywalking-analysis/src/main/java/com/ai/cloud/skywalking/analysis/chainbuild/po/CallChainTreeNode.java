package com.ai.cloud.skywalking.analysis.chainbuild.po;

import com.ai.cloud.skywalking.analysis.chainbuild.entity.ChainNodeForSummary;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

public class CallChainTreeNode {

    private String traceLevelId;

    // key: nodeToken
    private Map<String, ChainNodeForSummary> chainNodeContainer;


    public CallChainTreeNode(ChainNode node) {
        this.traceLevelId = node.getTraceLevelId();
        chainNodeContainer.put(node.getNodeToken(), new ChainNodeForSummary(node));
    }

    public CallChainTreeNode(String originData) {
        JsonObject jsonObject = (JsonObject) new JsonParser().parse(originData);
        traceLevelId = jsonObject.get("traceLevelId").getAsString();
        chainNodeContainer = new Gson().fromJson(jsonObject.get("chainNodeContainer").getAsString(),
                new TypeToken<Map<String, ChainNodeForSummary>>() {
                }.getType());
    }

    public void mergeIfNess(ChainNode node) {
        if (!chainNodeContainer.containsKey(node.getNodeToken())) {
            chainNodeContainer.put(node.getNodeToken(), new ChainNodeForSummary(node));
        }
    }

    public void summary(ChainNode node) {
        ChainNodeForSummary chainNode = chainNodeContainer.get(node.getNodeToken());
        chainNode.summary(node);
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
