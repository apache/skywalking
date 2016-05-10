package com.ai.cloud.skywalking.web.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xin on 16-4-28.
 */
public class TypicalCallTree {
    private String callTreeId;
    private Map<String, TypicalCallTreeNode> treeNodes;


    public TypicalCallTree(String callTreeId) {
        this.callTreeId = callTreeId;
        this.treeNodes = new HashMap<String, TypicalCallTreeNode>();
    }

    public void addNode(TypicalCallTreeNode typicalCallTreeNode) {
        this.treeNodes.put(typicalCallTreeNode.getNodeToken(), typicalCallTreeNode);
    }
}
