package com.ai.cloud.skywalking.web.entity;

import com.ai.cloud.skywalking.web.dto.AnlyResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xin on 16-4-6.
 */
public class CallChainTree {
    private String treeId;
    private String entranceViewpoint;
    private Map<String, String> nodes;
    private AnlyResult entranceAnlyResult;

    public CallChainTree(String treeId, String entranceViewpoint) {
        this.treeId = treeId;
        this.entranceViewpoint = entranceViewpoint;
        nodes = new HashMap<String, String>();
    }

    public void setEntranceAnlyResult(AnlyResult entranceAnlyResult) {
        this.entranceAnlyResult = entranceAnlyResult;
    }

    public void addNodes(Map<String, String> nodes) {
        this.nodes.putAll(nodes);
    }
}
