package com.a.eye.skywalking.collector.worker.globaltrace.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public class TreeNode {

    private String spanId;

    private List<TreeNode> childNodes;

    public TreeNode(String spanId) {
        this.spanId = spanId;
        childNodes = new ArrayList<>();
    }

    public void addChild(TreeNode childNode) {
        childNodes.add(childNode);
    }
}
