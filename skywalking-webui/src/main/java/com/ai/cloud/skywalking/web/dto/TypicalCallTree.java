package com.ai.cloud.skywalking.web.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 16-4-28.
 */
public class TypicalCallTree {
    private String callTreeId;
    private List<TypicalCallTreeNode> treeNodes;


    public TypicalCallTree(String callTreeId) {
        this.callTreeId = callTreeId;
        this.treeNodes = new ArrayList<TypicalCallTreeNode>();
    }

    public void addNode(TypicalCallTreeNode typicalCallTreeNode) {
        this.treeNodes.add(typicalCallTreeNode);
    }
}
